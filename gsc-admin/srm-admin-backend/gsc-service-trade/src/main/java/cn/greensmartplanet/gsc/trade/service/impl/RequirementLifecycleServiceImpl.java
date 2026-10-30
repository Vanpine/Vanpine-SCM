package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ConnectionClient;
import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.dto.PublishRequirementDTO;
import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import cn.greensmartplanet.gsc.trade.mapper.ProcurementRequirementMapper;
import cn.greensmartplanet.gsc.trade.service.RequirementLifecycleService;
import cn.greensmartplanet.gsc.trade.support.RequirementEmbedSync;
import cn.greensmartplanet.gsc.trade.vo.RequirementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 采购需求生命周期服务实现 — 发布 / 编辑 / 重新发布 / 关闭
 * <p>
 * 状态机：open → closed / expired / completed；closed/expired 可重新发布 → open，completed 已闭环不可回退。
 * 目标供应商数达标自动翻 completed（对接归供应商域，Feign 查询计数）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementLifecycleServiceImpl implements RequirementLifecycleService {

    private final ProcurementRequirementMapper requirementMapper;
    // 画像归供应商域，Feign 查询（发布校验采购方身份）
    private final ProfileClient profileClient;
    // 对接归供应商域，Feign 查询（目标供应商数达标判定）
    private final ConnectionClient connectionClient;
    private final RequirementEmbedSync requirementEmbedSync;

    /**
     * 发布采购需求：校验采购方画像（buyer/both），入库并返回 VO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementVO publish(Long tenantId, PublishRequirementDTO dto) {
        // 1. 校验画像存在（采购方/双身份才允许发布需求）——画像归供应商域，Feign 查询
        ProfileInfoDTO profile = profileClient.getByTenantId(tenantId).getData();
        if (profile == null || (!"buyer".equals(profile.getProfileType()) && !"both".equals(profile.getProfileType()))) {
            throw new BizException(ErrorCode.MATCH_PROFILE_NOT_FOUND);
        }

        // 2. DTO → Entity（联系人缺省回填画像联系方式）
        ProcurementRequirement req = ProcurementRequirement.builder()
                .tenantId(tenantId)
                .title(dto.getTitle())
                .category(dto.getCategory())
                .quantity(dto.getQuantity())
                .unit(dto.getUnit())
                .region(dto.getRegion())
                .certRequire(dto.getCertRequire())
                .deadline(dto.getDeadline())
                .budget(dto.getBudget())
                .description(dto.getDescription())
                .status(RequirementStatus.OPEN.getCode())
                .targetSupplierCount(dto.getTargetSupplierCount())
                .isPublic(dto.getIsPublic() != null ? dto.getIsPublic() : false)
                .contactName(dto.getContactName() != null && !dto.getContactName().isBlank() ? dto.getContactName() : profile.getContactName())
                .contactPhone(dto.getContactPhone() != null && !dto.getContactPhone().isBlank() ? dto.getContactPhone() : profile.getContactPhone())
                .contactEmail(dto.getContactEmail() != null && !dto.getContactEmail().isBlank() ? dto.getContactEmail() : profile.getContactEmail())
                .build();

        // 3. 入库
        requirementMapper.insert(req);
        requirementEmbedSync.scheduleSync(req);
        log.info("采购需求已发布, id={}, tenantId={}, title={}", req.getId(), tenantId, dto.getTitle());
        return RequirementVO.fromEntity(req);
    }

    /**
     * 编辑需求：仅本人 open 需求可编辑，同步校验目标供应商数达标自动翻 completed
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Long tenantId, PublishRequirementDTO dto) {
        // 1. 查需求是否存在
        ProcurementRequirement req = requirementMapper.selectById(id);
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        // 2. 权限校验：只能编辑自己的需求（红线 2）
        if (!req.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 3. 状态校验：非 open 不可编辑
        if (!RequirementStatus.OPEN.getCode().equals(req.getStatus())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }

        // 4. DTO → Entity 并更新（联系人缺省保留原值）
        req.setTitle(dto.getTitle());
        req.setCategory(dto.getCategory());
        req.setQuantity(dto.getQuantity());
        req.setUnit(dto.getUnit());
        req.setRegion(dto.getRegion());
        req.setCertRequire(dto.getCertRequire());
        req.setDeadline(dto.getDeadline());
        req.setBudget(dto.getBudget());
        req.setDescription(dto.getDescription());
        req.setTargetSupplierCount(dto.getTargetSupplierCount());
        req.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : req.getIsPublic());
        req.setContactName(dto.getContactName() != null && !dto.getContactName().isBlank() ? dto.getContactName() : req.getContactName());
        req.setContactPhone(dto.getContactPhone() != null && !dto.getContactPhone().isBlank() ? dto.getContactPhone() : req.getContactPhone());
        req.setContactEmail(dto.getContactEmail() != null && !dto.getContactEmail().isBlank() ? dto.getContactEmail() : req.getContactEmail());

        int rows = requirementMapper.updateById(id, tenantId, req);
        if (rows == 0) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }

        // 5. 同步状态：检查需求目标数与已接受对接数（对接归供应商域，Feign 查询）
        if (req.getTargetSupplierCount() != null && !RequirementStatus.CLOSED.getCode().equals(req.getStatus()) && !RequirementStatus.EXPIRED.getCode().equals(req.getStatus())) {
            long acceptedCount = connectionClient.countGroupByRequirementIds(tenantId, List.of(id))
                    .getData().getOrDefault(id, 0L);
            if (acceptedCount >= req.getTargetSupplierCount()) {
                // 5a. 达标 → completed
                if (!RequirementStatus.COMPLETED.getCode().equals(req.getStatus())) {
                    requirementMapper.updateStatus(id, RequirementStatus.COMPLETED.getCode());
                    req.setStatus(RequirementStatus.COMPLETED.getCode());
                    log.info("需求已达目标供应商数, id={}, accepted={}, target={}", id, acceptedCount, req.getTargetSupplierCount());
                }
            } else {
                // 5b. 未达标 + 当前 completed → 退回 open（目标数被调高）
                if (RequirementStatus.COMPLETED.getCode().equals(req.getStatus())) {
                    requirementMapper.updateStatus(id, RequirementStatus.OPEN.getCode());
                    req.setStatus(RequirementStatus.OPEN.getCode());
                    log.info("需求目标数已提高, 退回进行中, id={}, accepted={}, target={}", id, acceptedCount, req.getTargetSupplierCount());
                }
            }
        }

        requirementEmbedSync.scheduleSync(req);
        log.info("采购需求已编辑, id={}, tenantId={}", id, tenantId);
    }

    /**
     * 重新发布已关闭/过期需求：先翻 open 再更新字段，完成后重查是否达标
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void republish(Long id, Long tenantId, PublishRequirementDTO dto) {
        // 1. 查需求
        ProcurementRequirement req = requirementMapper.selectById(id);
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        // 2. 权限校验（红线 2）
        if (!req.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 3. open 状态（已完成需求增加供应商数量等场景）→ 直接编辑
        if (RequirementStatus.OPEN.getCode().equals(req.getStatus())) {
            update(id, tenantId, dto);
            return;
        }
        // 4. 仅 closed/expired 可重新发布；completed 已闭环，不可回退（需新发布）
        if (!RequirementStatus.CLOSED.getCode().equals(req.getStatus()) && !RequirementStatus.EXPIRED.getCode().equals(req.getStatus())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 5. 先翻状态（closed/expired/completed → open），绕过 updateById 的 status='open' 限制
        int rows = requirementMapper.republishById(id, tenantId);
        if (rows == 0) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 6. 再更新字段（此时 status 已是 open，updateById 的 WHERE 条件满足）
        req.setTitle(dto.getTitle());
        req.setCategory(dto.getCategory());
        req.setQuantity(dto.getQuantity());
        req.setUnit(dto.getUnit());
        req.setRegion(dto.getRegion());
        req.setCertRequire(dto.getCertRequire());
        req.setDeadline(dto.getDeadline());
        req.setBudget(dto.getBudget());
        req.setDescription(dto.getDescription());
        req.setTargetSupplierCount(dto.getTargetSupplierCount());
        req.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : req.getIsPublic());
        req.setContactName(dto.getContactName() != null && !dto.getContactName().isBlank() ? dto.getContactName() : req.getContactName());
        req.setContactPhone(dto.getContactPhone() != null && !dto.getContactPhone().isBlank() ? dto.getContactPhone() : req.getContactPhone());
        req.setContactEmail(dto.getContactEmail() != null && !dto.getContactEmail().isBlank() ? dto.getContactEmail() : req.getContactEmail());
        req.setStatus(RequirementStatus.OPEN.getCode());
        requirementMapper.updateById(id, tenantId, req);

        // 7. 重新发布后检查是否已达标 → completed
        if (req.getTargetSupplierCount() != null) {
            long acceptedCount = connectionClient.countGroupByRequirementIds(tenantId, List.of(id))
                    .getData().getOrDefault(id, 0L);
            if (acceptedCount >= req.getTargetSupplierCount()) {
                requirementMapper.updateStatus(id, RequirementStatus.COMPLETED.getCode());
                req.setStatus(RequirementStatus.COMPLETED.getCode());
                log.info("重新发布后仍达标, id={}, accepted={}, target={}", id, acceptedCount, req.getTargetSupplierCount());
            }
        }

        requirementEmbedSync.scheduleSync(req);
        log.info("需求已重新发布, id={}, tenantId={}", id, tenantId);
    }

    /**
     * 关闭需求：仅本人 open/completed 可关闭
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, Long tenantId) {
        // 1. 查需求是否存在
        ProcurementRequirement req = requirementMapper.selectById(id);
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        // 2. 权限校验：只能关闭自己的需求（红线 2）
        if (!req.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 3. 状态校验：非 open/completed 不可关闭
        if (!RequirementStatus.OPEN.getCode().equals(req.getStatus()) && !RequirementStatus.COMPLETED.getCode().equals(req.getStatus())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 4. 关闭
        int rows = requirementMapper.closeById(id, tenantId);
        if (rows == 0) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        req.setStatus(RequirementStatus.CLOSED.getCode());
        requirementEmbedSync.scheduleSync(req);
        log.info("采购需求已关闭, id={}, tenantId={}", id, tenantId);
    }
}
