package cn.greensmartplanet.gsc.supplier.service.impl;

// 实体类 — 数据库映射对象

import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.support.MatchCandidateRecall;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;

/**
 * AI 语义匹配策略 — 调用 Python LangChain 服务实现智能撮合
 *
 * <p>流程：
 * <ol>
 *   <li>SQL 按品类/地域召回（上限 80），不再扫全库 500</li>
 *   <li>构造 JSON 请求 → HTTP POST /api/match 到 Python AI 服务</li>
 *   <li>接收排序后的匹配结果（含 score + reasons）</li>
 *   <li>批量填充尽调信息（复用 RuleBasedMatchStrategy 逻辑）</li>
 *   <li>返回 {@link SupplierRecommendVO} 列表给前端</li>
 * </ol>
 *
 * <p>启用条件：配置 {@code gsc.match.engine=ai} 后本实现生效，
 * 否则默认走 {@link RuleBasedMatchStrategy}。
 *
 * @author gsc
 */
@Slf4j                                    // Lombok：自动生成 log 对象
@Component("aiMatchStrategy")             // Spring Bean，名称为 aiMatchStrategy
@ConditionalOnProperty(                   // 条件装配：仅当配置为 ai 时加载
        name = "gsc.match.engine",            // 配置项名称
        havingValue = "ai"                    // 等于 "ai" 时生效
)
@RequiredArgsConstructor                  // Lombok：为 final 字段生成构造器注入
public class AiMatchStrategy implements MatchStrategy {

    // ==================== 依赖注入 ====================

    private final MatchCandidateRecall matchCandidateRecall;       // SQL 召回，避免把全库丢给 Python
    private final DueDiligenceMapper dueDiligenceMapper;           // 尽调数据访问
    private final ApplicationClient applicationClient;             // 平台域 Feign：tenantId→applicationId 映射（尽调回填用）
    private final RestClient restClient;                           // 注入全局 RestClient（baseUrl + 超时已预设）

    // ==================== 核心方法 ====================

    @Override
    public List<SupplierRecommendVO> match(RequirementMatchFieldsDTO requirement, boolean needLlmReasons) {
        // 1. SQL 召回（已排除自己和纯买方），不再 COUNT + 拉 500 家
        long t0 = System.currentTimeMillis();
        List<EnterpriseProfile> candidates = matchCandidateRecall.recall(requirement);
        if (candidates.isEmpty()) {
            log.info("AI匹配: 召回为空, requirementId={}", requirement.getId());
            return Collections.emptyList();
        }
        log.info("AI匹配开始: requirementId={}, 召回候选数={}, recallMs={}",
                requirement.getId(), candidates.size(), System.currentTimeMillis() - t0);

        // 3. 构造 HTTP 请求 → 调用 Python AI 服务（复用 RestClient 单例）
        Map<String, Object> requestBody = buildMatchRequest(requirement, candidates, needLlmReasons);
        log.info("AI匹配: 向Python服务发送请求, candidates={}", candidates.size());

        // 4. HTTP POST 调用，同步等待结果
        Map<String, Object> response;
        try {
            long tPython = System.currentTimeMillis();
            response = restClient
                    .post()                                                          // HTTP POST
                    .uri("/api/match")                                               // Python 匹配端点
                    .contentType(MediaType.APPLICATION_JSON)                         // JSON 格式
                    .body(requestBody)                                               // 请求体
                    .retrieve()                                                      // 发送并获取响应
                    .body(Map.class);                                                // 反序列化为 Map
            log.info("AI匹配: Python返回, requirementId={}, pythonMs={}",
                    requirement.getId(), System.currentTimeMillis() - tPython);
        } catch (HttpClientErrorException e) {                                       // Python 返回 4xx（参数校验失败等）
            String respBody = e.getResponseBodyAsString();
            // 获取 Python 返回的错误详情
            log.error("AI匹配: Python返回4xx, status={}, body={}", e.getStatusCode(), respBody);
            throw new BizException(ErrorCode.PROCUREMENT_AI_ANALYSIS_FAILED,
                    "AI匹配失败: Python服务返回 " + e.getStatusCode() + ", " + respBody);
        } catch (HttpServerErrorException e) {                                       // Python 返回 5xx（内部错误）
            String respBody = e.getResponseBodyAsString();                           // 获取 Python 返回的错误详情
            log.error("AI匹配: Python返回5xx, status={}, body={}", e.getStatusCode(), respBody);
            throw new BizException(ErrorCode.PROCUREMENT_AI_ANALYSIS_FAILED,
                    "AI匹配失败: Python服务返回 " + e.getStatusCode() + ", " + respBody);
        } catch (Exception e) {                                                      // 网络/序列化等其他异常
            log.error("AI匹配: 调用Python服务异常, error={}", e.getMessage(), e);
            throw new BizException(ErrorCode.PROCUREMENT_AI_ANALYSIS_FAILED, "AI匹配失败: " + e.getMessage());
        }

        // 5. 解析响应 → VO 列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultList =
                (List<Map<String, Object>>) response.get("results");             // 提取 results 数组
        if (resultList == null || resultList.isEmpty()) {                        // 无匹配结果
            log.info("AI匹配: Python返回空结果, requirementId={}", requirement.getId());
            return Collections.emptyList();                                      // 返回空列表
        }

        // 构建 tenantId → SupplierRecommendVO 映射（后续填充尽调信息用）
        Map<Long, EnterpriseProfile> profileByTenant = new HashMap<>();
        for (EnterpriseProfile p : candidates) {
            profileByTenant.put(p.getTenantId(), p);
        }
        Map<Long, SupplierRecommendVO> voMap = new LinkedHashMap<>();            // 保持插入顺序
        for (Map<String, Object> item : resultList) {                            // 遍历匹配结果
            Long tenantId = ((Number) item.get("tenant_id")).longValue();        // 提取租户ID
            double score = ((Number) item.get("score")).doubleValue();           // 提取综合分
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) item.get("reasons");           // 提取理由列表

            EnterpriseProfile profile = profileByTenant.get(tenantId);
            if (profile == null) {                                               // 只在 Python 结果中但不在候选池
                continue;                                                        // 跳过（数据不一致）
            }

            SupplierRecommendVO vo = SupplierRecommendVO.fromProfile(profile);   // 基础字段复制
            vo.setMatchScore(BigDecimal.valueOf(score).setScale(1,               // 设置匹配分
                    java.math.RoundingMode.HALF_UP));                            // 保留1位小数
            // 使用 Python 返回的理由，为空时用兜底自然语言
            if (reasons != null && !reasons.isEmpty()) {
                vo.setMatchReasons(reasons);
            } else {
                vo.setMatchReasons(buildFallbackReasons(profile, BigDecimal.valueOf(score)));
            }
            voMap.put(tenantId, vo);                                             // 加入映射
        }

        // 6. 批量填充尽调信息（复用 RuleBasedMatchStrategy 的模式）
        fillDdInfo(voMap);                                                       // 填充 DD 字段

        // 7. 组装最终结果（与 voMap 顺序一致）
        List<SupplierRecommendVO> results = new ArrayList<>(voMap.values());     // 转为 List

        log.info("AI匹配完成: requirementId={}, 匹配结果数={}",
                requirement.getId(), results.size());
        return results;                                                          // 返回排序好的结果
    }

    // ==================== 请求构造 ====================

    /**
     * 构造发给 Python AI 服务的 JSON 请求体
     *
     * @param req             采购需求实体
     * @param candidates      候选供应商画像列表
     * @param needLlmReasons  异步补 AI 理由时 true；广场和详情主请求 false
     * @return Map 结构的请求体（自动序列化为 JSON）
     */
    private Map<String, Object> buildMatchRequest(
            RequirementMatchFieldsDTO req, List<EnterpriseProfile> candidates, boolean needLlmReasons) {
        // 组装需求信息
        Map<String, Object> requirement = new LinkedHashMap<>();                 // 需求部分
        requirement.put("title", req.getTitle() != null ? req.getTitle() : "");  // 需求标题
        requirement.put("category", req.getCategory() != null ? req.getCategory() : "");                                       // 采购品类
        requirement.put("cert_require", req.getCertRequire() != null ? req.getCertRequire() : "");                                    // 资质要求
        requirement.put("region", req.getRegion() != null ? req.getRegion() : "");                                         // 地域要求
        requirement.put("description", req.getDescription() != null ? req.getDescription() : "");                                    // 需求详细描述
        requirement.put("quantity", req.getQuantity() != null ? req.getQuantity() : "");                                       // 采购数量
        requirement.put("unit", req.getUnit() != null ? req.getUnit() : "");                                           // 单位
        requirement.put("budget", req.getBudget() != null ? req.getBudget() : "");                                         // 预算

        // 组装候选供应商列表
        List<Map<String, Object>> candidateList = new ArrayList<>();              // 候选列表
        for (EnterpriseProfile p : candidates) {                                  // 遍历每个画像
            Map<String, Object> c = new LinkedHashMap<>();                       // 单个候选
            c.put("tenant_id", p.getTenantId());                                 // 租户ID
            c.put("company_name", p.getCompanyName() != null
                    ? p.getCompanyName() : "");                                  // 公司名称
            c.put("sup_categories", p.getSupCategories() != null
                    ? p.getSupCategories() : "");                                // 生产品类
            c.put("sup_capacity", p.getSupCapacity() != null
                    ? p.getSupCapacity() : "");                                  // 年产能
            c.put("sup_certs", p.getSupCerts() != null
                    ? p.getSupCerts() : "");                                     // 资质认证
            c.put("sup_export", p.getSupExport() != null
                    ? p.getSupExport() : "0");                                   // 出口经验
            c.put("completeness_score", p.getCompletenessScore() != null
                    ? p.getCompletenessScore() : 0);                             // 画像完整度
            c.put("perf_score", p.getPerfScore());                               // 综合履约分（二期 AI 打分预留）
            c.put("main_products", p.getMainProducts() != null
                    ? p.getMainProducts() : "");                                 // 主要产品
            c.put("industry", p.getIndustry() != null
                    ? p.getIndustry() : "");                                     // 所属行业
            c.put("province", p.getProvince() != null
                    ? p.getProvince() : "");                                     // 省份
            c.put("city", p.getCity() != null ? p.getCity() : "");               // 城市
            candidateList.add(c);                                                // 加入列表
        }

        // 组装完整请求
        Map<String, Object> requestBody = new LinkedHashMap<>();                  // 请求体
        requestBody.put("requirement", requirement);                              // 需求部分
        requestBody.put("candidates", candidateList);                             // 候选列表
        // 广场 false：不调 DeepSeek。采购需求智能匹配 true：Top10 并发写理由
        requestBody.put("need_llm_reasons", needLlmReasons);
        return requestBody;                                                       // 返回请求体
    }

    // ==================== 尽调信息填充 ====================

    /**
     * 批量填充尽调信息到 VO
     *
     * <p>逻辑与 RuleBasedMatchStrategy 完全一致：
     * tenantId → Application → applicationId → DueDiligence → VO
     */
    private void fillDdInfo(Map<Long, SupplierRecommendVO> voMap) {
        if (voMap.isEmpty()) {                                                    // 空映射
            return;                                                               // 无需填充
        }

        // 4a. 批量查申请映射（⚠️ Feign 一次批量调用替代逐租户直查 application 表）
        Map<Long, Long> tenantToAppId = applicationClient
                .getIdsByTenantIds(new ArrayList<>(voMap.keySet())).getData();
        if (tenantToAppId == null) {
            tenantToAppId = Map.of();
        }

        if (tenantToAppId.isEmpty()) {                                            // 无申请记录
            return;                                                               // 无需填充
        }

        // 4b. 批量查 DueDiligence，建立 appId → DD 映射
        List<Long> appIds = new ArrayList<>(tenantToAppId.values());               // 收集所有 appId
        List<DueDiligence> ddList = dueDiligenceMapper
                .selectByApplicationIds(appIds);                                  // 批量查询尽调
        Map<Long, DueDiligence> appIdToDd = new HashMap<>();                      // appId → DD
        for (DueDiligence dd : ddList) {                                          // 遍历尽调记录
            appIdToDd.put(dd.getApplicationId(), dd);                             // 建立映射
        }

        // 4c. 填充到每个 VO
        for (Map.Entry<Long, SupplierRecommendVO> entry : voMap.entrySet()) {     // 遍历 VO 映射
            Long tenantId = entry.getKey();                                       // 租户ID
            SupplierRecommendVO vo = entry.getValue();                            // VO 对象
            Long appId = tenantToAppId.get(tenantId);                             // 查 appId
            if (appId == null) {                                                  // 无申请
                continue;                                                         // 跳过
            }
            DueDiligence dd = appIdToDd.get(appId);                              // 查尽调
            if (dd == null) {                                                     // 无尽调
                continue;                                                         // 跳过
            }
            // 填充 DD 相关信息到 VO
            vo.setDdStatus(dd.getDdFirmStatus());                                // 尽调状态
            vo.setDdReportUrl(dd.getDdReportKey());                              // 报告 URL
            vo.setDdReportName(dd.getDdReportName());                            // 报告名称
            vo.setDdFirmName(dd.getDdFirmName());                                // 事务所名称
            vo.setDdFirmRemark(dd.getDdFirmReturnReason());                       // 事务所结论/退回原因
            vo.setDdConfirmedAt(dd.getDdConfirmedAt());                          // 尽调确认时间
        }
    }

    // ==================== 理由兜底 ====================

    /**
     * Python 返回空理由时，用自然语言模板兜底
     */
    private List<String> buildFallbackReasons(EnterpriseProfile profile, BigDecimal score) {
        List<String> reasons = new ArrayList<>();
        // 品类
        if (profile.getSupCategories() != null && !profile.getSupCategories().isBlank()) {
            reasons.add("供应品类：" + profile.getSupCategories());
        }
        // 地域
        if (profile.getProvince() != null && !profile.getProvince().isBlank()) {
            reasons.add("所在地域：" + profile.getProvince()
                    + (profile.getCity() != null && !profile.getCity().isBlank() ? profile.getCity() : ""));
        }
        // 资质
        if (profile.getSupCerts() != null && !profile.getSupCerts().isBlank()) {
            reasons.add("资质认证：" + profile.getSupCerts());
        }
        // 出口经验
        String export = profile.getSupExport();
        if (export != null && !export.isBlank()) {
            String[] levels = {"无出口经验", "3年以下", "3-5年", "5-10年", "10年以上"};
            try {
                int idx = Integer.parseInt(export.trim());
                if (idx >= 0 && idx < levels.length) reasons.add("出口经验：" + levels[idx]);
            } catch (NumberFormatException ignored) {
            }
        }
        // 综合评分
        reasons.add("综合匹配度 " + score + " 分，推荐优先联系。");
        return reasons;
    }
}
