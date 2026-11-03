package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSummaryDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.framework.tenant.TenantContextHolder;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.projection.SupplierConnectionStatusRow;
import cn.greensmartplanet.gsc.supplier.service.ProfileQueryService;
import cn.greensmartplanet.gsc.supplier.vo.EnterpriseProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业画像查询服务实现 — 单查 / 公开画像 / 供应商广场列表 / 内部契约
 * <p>
 * 只读查询。列表关联数据（尽调摘要/对接状态）采用批量查询 + Map 组装，消 N+1（红线 1）。
 * <p>
 * ⚠️ 微服务拆分：尽调信息查询先经 ApplicationClient Feign 拿 tenantId→applicationId 映射，
 * 再按 applicationId 查本域 due_diligence 表——替代跨服务 JOIN application 表（消除跨域直查泄漏）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileQueryServiceImpl implements ProfileQueryService {

    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final DueDiligenceMapper dueDiligenceMapper;
    private final ConnectionMapper connectionMapper;
    // 平台域 Feign：入驻申请（application 表归平台域，tenantId→applicationId 映射用）
    private final ApplicationClient applicationClient;
    // OSS：尽调报告 key 现签短效 URL（AI 当轮展示）
    private final OssService ossService;

    /** 获取当前租户的企业画像（超管 tenantId=1 返回合成画像，前端入口用） */
    @Override
    public EnterpriseProfileVO getMyProfile(Long tenantId) {
        // 超管不需要企业画像，直接返回合成画像
        if (tenantId != null && tenantId == 1L) {
            EnterpriseProfileVO vo = new EnterpriseProfileVO();
            vo.setTenantId(1L);
            vo.setProfileType("both");
            vo.setCompanyName("平台管理");
            return vo;
        }
        EnterpriseProfile profile = enterpriseProfileMapper.selectByTenantId(tenantId);
        if (profile == null) {
            throw new BizException(ErrorCode.MATCH_PROFILE_NOT_FOUND);
        }
        return EnterpriseProfileVO.fromEntity(profile);
    }

    /** 查对方企业画像+尽调信息（对接记录/需求广场查看用；超管无画像抛业务异常） */
    @Override
    public EnterpriseProfileVO getPublicProfile(Long tenantId) {
        // 超管无企业画像，前端查看时返回空
        if (tenantId != null && tenantId == 1L) {
            throw new BizException(ErrorCode.NOT_FOUND, "企业画像不存在");
        }
        EnterpriseProfile profile = enterpriseProfileMapper.selectByTenantId(tenantId);
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "企业画像不存在");
        }
        EnterpriseProfileVO vo = EnterpriseProfileVO.fromEntity(profile);
        // 附尽调信息：Feign 拿 tenantId→applicationId → 按 applicationId 查（消除跨域 JOIN application 表）
        Map<Long, Long> appIdByTenant = applicationClient.getIdsByTenantIds(List.of(tenantId)).getData();
        Long appId = appIdByTenant != null ? appIdByTenant.get(tenantId) : null;
        if (appId != null) {
            DueDiligence dd = dueDiligenceMapper.selectByApplicationId(appId);
            if (dd != null) {
                vo.setDdFirmName(dd.getDdFirmName());
                vo.setDdFirmRemark(dd.getDdFirmReturnReason());
                vo.setDdReportUrl(dd.getDdReportKey());
            }
        }
        return vo;
    }

    /** 分页查供应商列表（排除当前租户），附尽调摘要、对接状态与数量 */
    @Override
    public Map<String, Object> listSuppliers(String keyword, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        // 排除当前租户自己（both 身份企业不把自己列为可联系的供应商）
        Long currentTenantId = TenantContextHolder.getTenantId();
        // 1. 查分页
        List<EnterpriseProfile> list = enterpriseProfileMapper.selectSuppliersPage(keyword, offset, pageSize, currentTenantId);

        long total = enterpriseProfileMapper.countSuppliers(keyword, currentTenantId);

        // 2. 批量查尽调信息：Feign 拿 tenantId→applicationId 映射 → 按 applicationId 批量查（消除跨域 JOIN）
        List<Long> tenantIds = list.stream().map(EnterpriseProfile::getTenantId).toList();
        Map<Long, DueDiligence> ddMap = new HashMap<>();
        if (!tenantIds.isEmpty()) {
            Map<Long, Long> appIdByTenant = applicationClient.getIdsByTenantIds(tenantIds).getData();
            if (appIdByTenant != null && !appIdByTenant.isEmpty()) {
                List<Long> appIds = new ArrayList<>(new HashSet<>(appIdByTenant.values()));
                Map<Long, DueDiligence> ddByAppId = new HashMap<>();
                for (DueDiligence dd : dueDiligenceMapper.selectByApplicationIds(appIds)) {
                    ddByAppId.putIfAbsent(dd.getApplicationId(), dd);
                }
                // 按租户回填：tenantId → 对应 applicationId 的尽调记录
                appIdByTenant.forEach((tenantId, appId) -> {
                    DueDiligence dd = ddByAppId.get(appId);
                    if (dd != null) ddMap.putIfAbsent(tenantId, dd);
                });
            }
        }

        // 2.5. 批量查当前采购商与各供应商的对接状态 + 数量（投影 DTO，替代 Map 强转）
        Map<Long, String> connStatusMap = new HashMap<>();
        Map<Long, Integer> connCountMap = new HashMap<>();
        if (!tenantIds.isEmpty()) {
            List<SupplierConnectionStatusRow> statusList = connectionMapper.selectStatusBySupplierIds(currentTenantId, tenantIds);
            for (SupplierConnectionStatusRow row : statusList) {
                connStatusMap.put(row.getSupplierTenantId(), row.getStatus());
                Integer cnt = row.getConnectionCount();
                connCountMap.put(row.getSupplierTenantId(), cnt != null ? cnt : 0);
            }
        }

        // 3. Entity→VO，附尽调摘要 + 对接状态
        List<EnterpriseProfileVO> records = list.stream()
                .map(p -> {
                    EnterpriseProfileVO vo = EnterpriseProfileVO.fromEntity(p);
                    DueDiligence dd = ddMap.get(p.getTenantId());
                    if (dd != null) {
                        vo.setDdFirmName(dd.getDdFirmName());
                        vo.setDdFirmRemark(dd.getDdFirmReturnReason());
                        vo.setDdReportUrl(dd.getDdReportKey());
                    }
                    vo.setMyConnectionStatus(connStatusMap.get(p.getTenantId()));
                    vo.setMyConnectionCount(connCountMap.get(p.getTenantId()));
                    return vo;
                })
                .toList();

        // 4. 组装分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    // ==================== 内部契约（平台/交易/交付域 Feign 调用） ====================

    /** 按租户ID查画像信息契约 DTO（不存在返回 null） */
    @Override
    public ProfileInfoDTO getInfoByTenantId(Long tenantId) {
        EnterpriseProfile profile = enterpriseProfileMapper.selectByTenantId(tenantId);
        return profile == null ? null : toInfoDTO(profile);
    }

    /** 批量按租户ID查画像契约 DTO（不存在的不在 map 中） */
    @Override
    public Map<Long, ProfileInfoDTO> getInfoByTenantIds(List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProfileInfoDTO> map = new HashMap<>();
        for (EnterpriseProfile profile : enterpriseProfileMapper.selectByTenantIds(tenantIds)) {
            map.put(profile.getTenantId(), toInfoDTO(profile));
        }
        return map;
    }

    /** 画像实体 → 契约 DTO（仅其他域需要的字段子集） */
    private ProfileInfoDTO toInfoDTO(EnterpriseProfile p) {
        return ProfileInfoDTO.builder()
                .tenantId(p.getTenantId())
                .companyName(p.getCompanyName())
                .profileType(p.getProfileType())
                .contactName(p.getContactName())
                .contactPhone(p.getContactPhone())
                .contactEmail(p.getContactEmail())
                .legalRep(p.getLegalRep())
                .registeredAddress(p.getRegisteredAddress())
                .country(p.getCountry())
                .province(p.getProvince())
                .city(p.getCity())
                .completenessScore(p.getCompletenessScore())
                .build();
    }

    // ==================== 内部契约（AI 域工具查询） ====================

    /** AI 工具 search_suppliers：按关键词搜索供应商摘要，批量回填尽调以拼准入状态 */
    @Override
    public List<AiSupplierSummaryDTO> searchForAi(String keyword, int page, int pageSize) {
        // 分页参数兜底 + 防越界（负值或过大一律收敛到安全范围，红线 5）
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (safePage - 1) * safeSize;
        // 1. 分页查供应商画像（与需求广场同款 SQL：profile_type IN supplier/both，排除当前租户）
        List<EnterpriseProfile> list = enterpriseProfileMapper.selectSuppliersPage(
                keyword, offset, safeSize, TenantContextHolder.getTenantId());
        // 2. 批量查尽调记录（Feign 拿 tenantId→applicationId → 按 applicationId 批量查，消除跨域 JOIN）
        Map<Long, DueDiligence> ddMap = loadDueDiligenceByTenantIds(
                list.stream().map(EnterpriseProfile::getTenantId).toList());
        // 3. 实体 → AI 摘要 DTO，按尽调记录拼准入状态
        return list.stream()
                .map(p -> AiSupplierSummaryDTO.builder()
                        .tenantId(p.getTenantId())
                        .companyName(p.getCompanyName())
                        .industry(p.getIndustry())
                        .supCategories(p.getSupCategories())
                        .province(p.getProvince())
                        .city(p.getCity())
                        .supCerts(p.getSupCerts())
                        .accessStatus(resolveAccessStatus(ddMap.get(p.getTenantId())))
                        .build())
                .toList();
    }

    /**
     * 画像是否为「供应商型」（可被采购方检索/查看）。
     * <p>
     * 与 {@code EnterpriseProfileMapper.selectSuppliersPage} 的
     * {@code profile_type IN ('supplier','both')} 保持一致——列表接口用 SQL 过滤，
     * 详情接口必须用同一规则，否则会出现「列表查不到、详情却能拿到」的越权面。
     *
     * @param profileType 画像类型（buyer / supplier / both）
     * @return true 表示可对外可见
     */
    private boolean isSupplierProfile(String profileType) {
        return "supplier".equals(profileType) || "both".equals(profileType);
    }

    /** AI 工具 get_supplier_detail：按租户ID查供应商详情（含完整画像 + 尽调报告现签 URL；不存在返回 null） */
    @Override
    public AiSupplierDetailDTO getDetailForAi(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        EnterpriseProfile profile = enterpriseProfileMapper.selectByTenantId(tenantId);
        if (profile == null) {
            return null;
        }
        // 可见性校验：只放行供应商型画像，与 searchForAi 的 profile_type IN ('supplier','both') 保持一致。
        // 缺这道校验时，AI 工具可凭任意 tenantId 拿到纯采购方租户的完整画像
        // （年营收 / 主要客户 / 员工规模等），而列表接口是查不到这些租户的。
        if (!isSupplierProfile(profile.getProfileType())) {
            log.info("AI 查供应商详情被拒：目标非供应商型画像 tenantId={}, profileType={}",
                    tenantId, profile.getProfileType());
            return null;
        }
        // 单条尽调记录：复用批量装载逻辑，避免重复 Feign 组装
        Map<Long, DueDiligence> ddMap = loadDueDiligenceByTenantIds(List.of(tenantId));
        DueDiligence dd = ddMap.get(tenantId);
        return AiSupplierDetailDTO.builder()
                .tenantId(profile.getTenantId())
                .profileType(profile.getProfileType())
                .companyName(profile.getCompanyName())
                .industry(profile.getIndustry())
                .supCategories(profile.getSupCategories())
                .supCerts(profile.getSupCerts())
                .province(profile.getProvince())
                .city(profile.getCity())
                .mainProducts(profile.getMainProducts())
                .description(profile.getDescription())
                .regNumber(profile.getRegNumber())
                .establishedYear(profile.getEstablishedYear())
                .supCapacity(profile.getSupCapacity())
                .supExport(profile.getSupExport())
                .keyClients(profile.getKeyClients())
                .leadTime(profile.getLeadTime())
                .employeeCount(profile.getEmployeeCount())
                .annualRevenue(profile.getAnnualRevenue())
                .annualRevenueAmount(profile.getAnnualRevenueAmount())
                .annualRevenueCurrency(profile.getAnnualRevenueCurrency())
                .fiscalPeriodStart(profile.getFiscalPeriodStart())
                .fiscalPeriodEnd(profile.getFiscalPeriodEnd())
                .completenessScore(profile.getCompletenessScore())
                .perfScore(profile.getPerfScore())
                .onTimeRate(profile.getOnTimeRate())
                .acceptPassRate(profile.getAcceptPassRate())
                .returnCount(profile.getReturnCount())
                .perfSampleSize(profile.getPerfSampleSize())
                .accessStatus(resolveAccessStatus(dd))
                .ddFirmName(dd == null ? null : dd.getDdFirmName())
                .ddReportStatus(dd == null ? null : dd.getDdReportStatus())
                .ddReportName(dd == null ? null : dd.getDdReportName())
                .ddReportUrl(resolveDdReportUrl(dd))
                .ddConfirmedAt(dd == null ? null : dd.getDdConfirmedAt())
                .build();
    }

    /**
     * 尽调报告 OSS key → 现签短效 URL（AI 当轮展示用）。
     * <p>
     * 报告 key 落库，访问时现签（600 秒）；无报告（key 为空 / 记录不存在）返回 null，AI 不展示链接。
     * 签 URL 失败仅告警降级为 null，不让单个报告链接异常拖垮整个供应商详情查询。
     */
    private String resolveDdReportUrl(DueDiligence dd) {
        if (dd == null || dd.getDdReportKey() == null || dd.getDdReportKey().isBlank()) {
            return null;
        }
        try {
            return ossService.generateGetUrl(dd.getDdReportKey());
        } catch (Exception e) {
            log.warn("尽调报告 URL 现签失败: applicationId={}, key={}, error={}",
                    dd.getApplicationId(), dd.getDdReportKey(), e.getMessage());
            return null;
        }
    }

    /**
     * 批量按租户ID查尽调记录（tenantId → DueDiligence）。
     * <p>
     * 尽调表按 applicationId 索引、租户ID需经 platform 域 application 表映射，故先 Feign 拿
     * tenantId→applicationId 映射，再按 applicationId 批量查本域 due_diligence 表（消 N+1）。
     */
    private Map<Long, DueDiligence> loadDueDiligenceByTenantIds(List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> appIdByTenant = applicationClient.getIdsByTenantIds(tenantIds).getData();
        if (appIdByTenant == null || appIdByTenant.isEmpty()) {
            return Map.of();
        }
        // 去重 applicationId（一个租户只对应一个申请，但防御去重避免重复查询）
        List<Long> appIds = appIdByTenant.values().stream().distinct().toList();
        // applicationId → DueDiligence（去重：同一申请多条时保留首条）
        Map<Long, DueDiligence> ddByAppId = dueDiligenceMapper.selectByApplicationIds(appIds).stream()
                .collect(Collectors.toMap(
                        DueDiligence::getApplicationId, dd -> dd, (a, b) -> a));
        // tenantId → DueDiligence：按映射回填
        Map<Long, DueDiligence> ddMap = new HashMap<>();
        appIdByTenant.forEach((tid, appId) -> {
            DueDiligence dd = ddByAppId.get(appId);
            if (dd != null) {
                ddMap.putIfAbsent(tid, dd);
            }
        });
        return ddMap;
    }

    /**
     * 拼接供应商准入状态（AI 直接读值，不推理）。
     * <p>
     * approved  = 有尽调记录 且 报告已完成(ddReportStatus=completed) 且 管理员已最终确认(ddConfirmedAt≠null)
     * in_review = 有尽调记录但未满足 approved（报告未完成 / 管理员未确认）
     * not_submitted = 无尽调记录
     */
    private String resolveAccessStatus(DueDiligence dd) {
        if (dd == null) {
            return "not_submitted";
        }
        boolean confirmed = "completed".equals(dd.getDdReportStatus()) && dd.getDdConfirmedAt() != null;
        return confirmed ? "approved" : "in_review";
    }
}
