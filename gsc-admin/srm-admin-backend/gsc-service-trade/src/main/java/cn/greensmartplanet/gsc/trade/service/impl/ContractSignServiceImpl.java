package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.service.ContractSignService;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.support.ContractAccessValidator;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同签署服务实现 — 上传盖章件 / 确认盖章件签署
 * <p>
 * 签署状态机：pending_supplier(待供应商确认) → pending_confirm(待确认盖章件) → signed(已签署)。
 * 防自确认：本方确认的是"对方"上传的盖章件，对方盖章件未上传时禁止确认（天然防自确认）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractSignServiceImpl implements ContractSignService {

    private final ContractMapper contractMapper;
    private final ContractAccessValidator contractAccessValidator;
    private final NotificationService notificationService;
    private final ProfileNameHelper profileNameHelper;

    /**
     * 上传盖章件：任一方 + pending_supplier/pending_confirm，上传后通知对方
     *
     * @param key 盖章件 OSS object key（不是签名 URL——签名会过期，故只存 key）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadSign(Long id, Long tenantId, Long userId, String key) {
        // 1. 盖章件 key 必填
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_SIGN_ATTACHMENT_REQUIRED);
        }
        // 2. 任一方归属校验 + 状态校验（红线 2）
        Contract contract = contractAccessValidator.getContractAnyParty(id, tenantId);
        String status = contract.getStatus();
        if (!ContractStatus.PENDING_SUPPLIER.getCode().equals(status)
                && !ContractStatus.PENDING_CONFIRM.getCode().equals(status)) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_UPLOAD_SIGN);
        }
        // 2b. 待供应商确认阶段：须先由供应商确认条款，才可上传盖章件
        if (ContractStatus.PENDING_SUPPLIER.getCode().equals(status)
                && !Integer.valueOf(1).equals(contract.getSupplierConfirmed())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_SUPPLIER_NOT_CONFIRMED);
        }
        // 3. 落库盖章件 key（Mapper 内控制 pending_supplier/pending_confirm → pending_confirm）
        int n = contractMapper.uploadSign(id, tenantId, userId, key.trim());
        if (n == 0) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        log.info("盖章件已上传: id={}, uploaderTenantId={}", id, tenantId);

        // 4. 站内消息：通知对方上传了盖章件（企业名降级"对方"兜底，不影响主流程）
        Long receiverTenantId = tenantId.equals(contract.getBuyerTenantId()) ? contract.getSupplierTenantId() : contract.getBuyerTenantId();
        String actorCompanyName = profileNameHelper.getCompanyName(tenantId);
        notificationService.notify(Notification.builder()
                .type("CONTRACT_UPLOAD_SIGN")
                .title("合同盖章件已上传")
                .content("【" + (actorCompanyName != null ? actorCompanyName : "对方") + "】已上传合同《" + contract.getTitle() + "》盖章件")
                .receiverTenantId(receiverTenantId)
                .relatedType("contract")
                .relatedId(contract.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }

    /**
     * 确认盖章件签署：任一方 + pending_confirm，对方盖章件须已上传（天然防自确认），signed 后 signDate=当天
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSign(Long id, Long tenantId, Long userId) {
        // 1. 任一方归属校验 + pending_confirm 状态（红线 2）
        Contract contract = contractAccessValidator.getContractAnyParty(id, tenantId);
        if (!ContractStatus.PENDING_CONFIRM.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_SIGN);
        }
        // 2. 本方确认的是对方上传的盖章件 → 对方盖章件须已上传（天然防自确认）
        boolean isBuyer = tenantId.equals(contract.getBuyerTenantId());
        String counterpartUrl = isBuyer
                ? contract.getSupplierSignAttachmentUrl()
                : contract.getBuyerSignAttachmentUrl();
        if (counterpartUrl == null || counterpartUrl.isBlank()) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_SIGN_ATTACHMENT_REQUIRED);
        }
        // 3. 确认签署（Mapper 内控制 pending_confirm → signed + sign_date）
        int n = contractMapper.confirmSign(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_SIGN);
        }
        log.info("盖章件已确认: id={}, confirmByTenantId={}", id, tenantId);
    }

    /**
     * sign 旧路径兼容：语义已改为确认盖章件签署
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long id, Long tenantId, Long userId) {
        confirmSign(id, tenantId, userId);
    }
}
