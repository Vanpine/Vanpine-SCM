package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.platform.entity.Application;
import cn.greensmartplanet.gsc.platform.entity.SanctionEntry;
import cn.greensmartplanet.gsc.platform.entity.SanctionScreenRecord;
import cn.greensmartplanet.gsc.platform.mapper.ApplicationMapper;
import cn.greensmartplanet.gsc.platform.mapper.SanctionScreenRecordMapper;
import cn.greensmartplanet.gsc.platform.service.SanctionScreeningService;
import cn.greensmartplanet.gsc.platform.support.SanctionNameIndex;
import cn.greensmartplanet.gsc.platform.vo.SanctionMatchVO;
import cn.greensmartplanet.gsc.platform.vo.SanctionScreenResultVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 制裁筛查服务实现 — 基于内存索引的名称精确/别名匹配
 * <p>
 * 性能：7.2 万条名单常驻内存 HashMap（{@link SanctionNameIndex}），筛查 O(1) 命中，
 * 不再全表查 DB + 逐条正则。单次筛查毫秒级。
 * <p>
 * 匹配策略（降同名误报）：
 * <ol>
 *   <li>名称规范化：小写、去空白与常见标点（公司后缀差异如 Co./Ltd. 归一）</li>
 *   <li>精确匹配：规范化后的公司名与名单 name / 别名完全相等 → 强命中（黄牌）</li>
 *   <li>名单库为空 → status=pending，不阻断</li>
 * </ol>
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionScreeningServiceImpl implements SanctionScreeningService {

    private final SanctionScreenRecordMapper screenRecordMapper;
    private final ApplicationMapper applicationMapper;
    private final ObjectMapper objectMapper;
    private final SanctionNameIndex nameIndex;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SanctionScreenResultVO screen(Long applicationId) {
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new cn.greensmartplanet.gsc.common.exception.BizException(
                    cn.greensmartplanet.gsc.common.exception.ErrorCode.APPLICATION_NOT_FOUND);
        }

        String companyName = app.getCompanyName();
        String regNo = app.getRegNumber();

        // 名单库为空 → pending，不阻断
        if (nameIndex.isEmpty()) {
            return save(app, "pending", 0, List.of());
        }

        // 内存索引精确匹配（公司名；注册号名单里无独立列，暂不做）
        List<SanctionEntry> hits = new ArrayList<>();
        SanctionEntry hit = nameIndex.lookup(companyName);
        if (hit != null) {
            hits.add(hit);
        }

        // 组装命中明细
        List<SanctionMatchVO> matchVos = hits.stream()
                .map(e -> {
                    SanctionMatchVO m = new SanctionMatchVO();
                    m.setEntryName(e.getName());
                    m.setProgram(e.getProgram());
                    m.setJurisdiction(e.getJurisdiction());
                    m.setEntityType(e.getEntityType());
                    return m;
                })
                .toList();

        String status = hits.isEmpty() ? "clean" : "warning";
        return save(app, status, hits.size(), matchVos);
    }

    /**
     * 落库筛查记录并返回结果。matches 序列化为 JSON 存 matches_json。
     */
    private SanctionScreenResultVO save(Application app, String status, int hitCount, List<SanctionMatchVO> matches) {
        String matchesJson = null;
        try {
            matchesJson = objectMapper.writeValueAsString(matches);
        } catch (JsonProcessingException e) {
            log.warn("筛查命中明细序列化失败, applicationId={}", app.getId(), e);
        }

        SanctionScreenRecord record = new SanctionScreenRecord();
        record.setId(IdWorker.getId());
        record.setApplicationId(app.getId());
        record.setTenantId(app.getTenantId());
        record.setQueryName(app.getCompanyName());
        record.setQueryRegNo(app.getRegNumber());
        record.setHitCount(hitCount);
        record.setStatus(status);
        record.setMatchesJson(matchesJson);
        record.setScreenedAt(LocalDateTime.now());
        screenRecordMapper.insert(record);

        log.info("制裁筛查完成: applicationId={}, company={}, status={}, hitCount={}",
                app.getId(), app.getCompanyName(), status, hitCount);

        SanctionScreenResultVO vo = new SanctionScreenResultVO();
        vo.setApplicationId(app.getId());
        vo.setStatus(status);
        vo.setHitCount(hitCount);
        vo.setMatches(matches);
        vo.setScreenedAt(record.getScreenedAt());
        return vo;
    }
}
