package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementRecallQueryDTO;
import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import cn.greensmartplanet.gsc.trade.mapper.ProcurementRequirementMapper;
import cn.greensmartplanet.gsc.trade.service.RequirementInternalService;
import cn.greensmartplanet.gsc.trade.support.RequirementEmbedSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购需求内部契约服务实现 — 供应商域 Feign 调用的契约方法
 * <p>
 * 契约语义（7.1）：查询不存在返回 data=null（HTTP 200），批量缺失不在 map 中；
 * 本类只做 Entity→DTO 转换，不涉及前端业务。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementInternalServiceImpl implements RequirementInternalService {

    private final ProcurementRequirementMapper requirementMapper;
    private final RequirementEmbedSync requirementEmbedSync;

    /**
     * 按需求ID查基础信息契约 DTO（不存在返回 null）
     */
    @Override
    public RequirementInfoDTO getInfoById(Long id) {
        ProcurementRequirement req = requirementMapper.selectById(id);
        return req == null ? null : RequirementInfoDTO.builder()
                .id(req.getId())
                .tenantId(req.getTenantId())
                .title(req.getTitle())
                .status(req.getStatus())
                .targetSupplierCount(req.getTargetSupplierCount())
                .build();
    }

    /**
     * 按需求ID查智能匹配字段契约 DTO（不存在返回 null）
     */
    @Override
    public RequirementMatchFieldsDTO getMatchFields(Long id) {
        ProcurementRequirement req = requirementMapper.selectById(id);
        return toMatchFields(req);
    }

    /**
     * 批量按需求ID查基础信息契约 DTO（不存在的需求不在 map 中）
     */
    @Override
    public Map<Long, RequirementInfoDTO> getInfoByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, RequirementInfoDTO> map = new HashMap<>();
        for (ProcurementRequirement req : requirementMapper.selectBatchIds(ids)) {
            map.put(req.getId(), RequirementInfoDTO.builder()
                    .id(req.getId())
                    .tenantId(req.getTenantId())
                    .title(req.getTitle())
                    .status(req.getStatus())
                    .targetSupplierCount(req.getTargetSupplierCount())
                    .build());
        }
        return map;
    }

    /**
     * 对接接受后回调：目标供应商数达标则 open → completed（幂等：已 completed 返回 true）
     */
    @Override
    public boolean completeIfTargetReached(Long id, long acceptedCount, int targetSupplierCount) {
        // 1. 未达标直接返回 false（不翻状态）
        if (acceptedCount < targetSupplierCount) {
            return false;
        }
        // 2. 需求不存在返回 false
        ProcurementRequirement req = requirementMapper.selectById(id);
        if (req == null) {
            return false;
        }
        // 3. 已 completed → 幂等返回 true
        if (RequirementStatus.COMPLETED.getCode().equals(req.getStatus())) {
            return true;
        }
        // 4. 翻 completed
        boolean updated = requirementMapper.updateStatus(id, RequirementStatus.COMPLETED.getCode()) > 0;
        if (updated) {
            req.setStatus(RequirementStatus.COMPLETED.getCode());
            requirementEmbedSync.scheduleSync(req);
        }
        return updated;
    }

    @Override
    public List<RequirementMatchFieldsDTO> recallOpenForMatch(RequirementRecallQueryDTO query) {
        if (query == null) {
            return List.of();
        }
        requirementMapper.updateExpired();
        int limit = query.getLimit() == null || query.getLimit() < 1 ? 80 : Math.min(query.getLimit(), 80);
        List<String> tokens = query.getCategoryTokens() == null ? List.of() : query.getCategoryTokens();
        String region = query.getRegion() == null || query.getRegion().isBlank() ? null : query.getRegion().trim();
        return requirementMapper.selectOpenForRecall(query.getExcludeTenantId(), tokens, region, limit)
                .stream()
                .map(this::toMatchFields)
                .toList();
    }

    private RequirementMatchFieldsDTO toMatchFields(ProcurementRequirement req) {
        if (req == null) {
            return null;
        }
        return RequirementMatchFieldsDTO.builder()
                .id(req.getId())
                .tenantId(req.getTenantId())
                .title(req.getTitle())
                .category(req.getCategory())
                .certRequire(req.getCertRequire())
                .region(req.getRegion())
                .description(req.getDescription())
                .quantity(req.getQuantity())
                .unit(req.getUnit())
                .budget(req.getBudget())
                .status(req.getStatus())
                .deadline(req.getDeadline())
                .createTime(req.getCreateTime())
                .targetSupplierCount(req.getTargetSupplierCount())
                .isPublic(req.getIsPublic())
                .build();
    }
}
