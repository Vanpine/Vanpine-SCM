package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.dto.PartialRejectDTO;
import cn.greensmartplanet.gsc.delivery.dto.RejectDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReturnLogisticsDTO;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.service.DeliveryLifecycleService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryReturnService;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import cn.greensmartplanet.gsc.delivery.support.DeliveryAssessmentService;
import cn.greensmartplanet.gsc.delivery.support.DeliveryNoGenerator;
import cn.greensmartplanet.gsc.delivery.support.DeliveryTraceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交付退货状态机服务实现
 * <p>
 * received → return_pending → return_confirmed → return_shipping → returned 闭环，
 * 异议回退 received、部分残次生成补发批。归属校验复用 Lifecycle 基座，终态评估委托 Assessment。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryReturnServiceImpl implements DeliveryReturnService {

    private final DeliveryMapper deliveryMapper;
    // 画像归供应商域，Feign 查询（通知文案企业名）
    private final ProfileClient profileClient;
    private final NotificationService notificationService;
    // 生命周期基座：getDeliveryOrThrow 归属校验（避免本服务重复实现）
    private final DeliveryLifecycleService lifecycleService;
    // 交付编号生成（补发批建单复用）
    private final DeliveryNoGenerator deliveryNoGenerator;
    // 交付轨迹落库
    private final DeliveryTraceSupport traceSupport;
    // 交付终态评估（违约金/履约表现）
    private final DeliveryAssessmentService assessmentService;

    /** 采购商验收不合格：填原因+凭证，进入待供应商确认，通知供应商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long tenantId, Long userId, RejectDeliveryDTO dto) {
        // 1. 校验退回原因 + 采购方身份
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new BizException(ErrorCode.DELIVERY_RETURN_REASON_REQUIRED);
        }
        Delivery d = lifecycleService.getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        String reason = dto.getReason().trim();
        // 2. 更新为待供应商确认 + 记录轨迹
        int n = deliveryMapper.rejectDelivery(id, tenantId, reason, dto.getReturnAttachmentKey().trim(), userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        traceSupport.addTrace(id, "return_request", "采购商验收不合格，等待供应商确认退回", tenantId, userId, reason);

        // 3. 通知供应商确认退回
        ProfileInfoDTO buyer = profileClient.getByTenantId(d.getBuyerTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RETURN_REQUEST")
                .title("交付验收不合格，待确认退回")
                .content("【" + (buyer != null ? buyer.getCompanyName() : "采购方") + "】验收不合格：" + reason
                        + "，请到发货管理中确认退回或提出异议")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("交付单验收不合格: id={}, buyerTenantId={}, reason={}", id, tenantId, reason);
        assessmentService.recalcSupplierPerformance(d.getSupplierTenantId());
    }

    /** 采购商部分残次：合格部分验收通过、残次部分生成补发批，通知供应商补发 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void partialReject(Long id, Long tenantId, Long userId, PartialRejectDTO dto) {
        // 1. 校验采购方身份 + 残次数量合法性（仅普通批可发起，1 ≤ 残次 < 实收）
        Delivery d = lifecycleService.getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (d.getReshipFrom() != null) {
            throw new BizException(ErrorCode.DELIVERY_RESHIP_NOT_ALLOWED);
        }
        Integer receiveQty = d.getReceiveQty();
        Integer shipQty = d.getShipQty() != null ? d.getShipQty() : d.getQuantity();
        int diff = (shipQty != null ? shipQty : 0) - (receiveQty != null ? receiveQty : 0);
        int defectiveQty = dto.getDefectiveQty();
        // 残次/缺货数量 = 发货数 - 实收数，补发不得超出差额
        if (receiveQty == null || shipQty == null || diff < 1 || defectiveQty < 1 || defectiveQty > diff) {
            throw new BizException(ErrorCode.DELIVERY_RESHIP_QTY_INVALID);
        }
        String reason = dto.getReason().trim();
        // 2. 原批置部分验收（accepted + partial），记录残次数量与原因/凭证
        int n = deliveryMapper.partialReject(id, tenantId, defectiveQty, reason,
                dto.getReturnAttachmentKey().trim(), userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        // 3. 新建补发批（免费不参与合同守恒，reship_from 指向原批；复用原批批次号，不占用新批次号）
        int batchNo = d.getBatchNo() != null ? d.getBatchNo() : 1;
        Delivery reship = Delivery.builder()
                .deliveryNo(deliveryNoGenerator.generate())
                .contractId(d.getContractId())
                .inquiryId(d.getInquiryId())
                .batchNo(batchNo)
                .buyerTenantId(d.getBuyerTenantId())
                .supplierTenantId(d.getSupplierTenantId())
                .quantity(defectiveQty)
                .amount(null)
                .currency(d.getCurrency())
                .deliveryTerms(d.getDeliveryTerms())
                .deliveryPlace(d.getDeliveryPlace())
                .deliveryDate(d.getDeliveryDate())
                .status(DeliveryStatus.RESHIP_PENDING.getCode())
                .reshipFrom(d.getId())
                .createBy(userId)
                .build();
        deliveryMapper.insert(reship);

        // 4. 记时间线（原批 + 补发批各一条）
        traceSupport.addTrace(id, "partial_reject", "采购商验收部分残次，合格部分验收通过，残次 " + defectiveQty + " 件发起补发",
                tenantId, userId, reason);

        // 部分验收也算交付完成：计算违约金快照
        assessmentService.calcPenalty(d);
        traceSupport.addTrace(reship.getId(), "create", "第 " + batchNo + " 批部分残次，生成补发单（应补 " + defectiveQty + " 件）",
                null, null, null);

        // 5. 通知供应商补发
        ProfileInfoDTO buyer = profileClient.getByTenantId(d.getBuyerTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RESHIP_REQUEST")
                .title("交付部分残次，待补发")
                .content("【" + (buyer != null ? buyer.getCompanyName() : "采购方") + "】验收部分残次：" + reason
                        + "，残次 " + defectiveQty + " 件，请在发货管理中补发")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(reship.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("部分残次补发: id={}, defectiveQty={}, reshipId={}", id, defectiveQty, reship.getId());
        assessmentService.recalcSupplierPerformance(d.getSupplierTenantId());
    }

    /** 供应商确认退回：进入待采购商填退货物流，通知采购商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long id, Long tenantId, Long userId) {
        Delivery d = lifecycleService.getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        int n = deliveryMapper.confirmReturn(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        traceSupport.addTrace(id, "return_confirm", "供应商确认退回", tenantId, userId, null);

        ProfileInfoDTO supplier = profileClient.getByTenantId(d.getSupplierTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RETURN_CONFIRMED")
                .title("供应商已确认退回")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商")
                        + "】已确认退回，请在验收管理中填写退货物流")
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("供应商确认退回: id={}, supplierTenantId={}", id, tenantId);
    }

    /** 供应商提出异议：回到待验收，异议原因记时间线，通知采购商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disputeReturn(Long id, Long tenantId, Long userId, String remark) {
        Delivery d = lifecycleService.getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        int n = deliveryMapper.disputeReturn(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        String disputeRemark = remark != null ? remark.trim() : null;
        if (disputeRemark != null && disputeRemark.isEmpty()) {
            disputeRemark = null;
        }
        traceSupport.addTrace(id, "return_dispute", "供应商提出异议，退回重新验收", tenantId, userId, disputeRemark);

        ProfileInfoDTO supplier = profileClient.getByTenantId(d.getSupplierTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RETURN_DISPUTE")
                .title("供应商对验收结果提出异议")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】对验收不合格提出异议"
                        + (disputeRemark != null ? "：" + disputeRemark : ""))
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("供应商提出异议: id={}, supplierTenantId={}, remark={}", id, tenantId, disputeRemark);
    }

    /** 采购商填写退货物流：退货在途，通知供应商确认收货 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fillReturnLogistics(Long id, Long tenantId, Long userId, ReturnLogisticsDTO dto) {
        Delivery d = lifecycleService.getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        int n = deliveryMapper.fillReturnLogistics(id, tenantId, dto.getReturnShipCompany().trim(),
                dto.getReturnShipTrackingNo().trim(), userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        traceSupport.addTrace(id, "return_logistics", "采购商填写退货物流（" + dto.getReturnShipCompany().trim()
                        + " / " + dto.getReturnShipTrackingNo().trim() + "）",
                tenantId, userId, null);

        ProfileInfoDTO buyer = profileClient.getByTenantId(d.getBuyerTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RETURN_LOGISTICS")
                .title("退货已发出，待确认收货")
                .content("【" + (buyer != null ? buyer.getCompanyName() : "采购方") + "】已安排退货物流："
                        + dto.getReturnShipCompany() + " " + dto.getReturnShipTrackingNo() + "，请确认收到退货")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("采购商填写退货物流: id={}, buyerTenantId={}", id, tenantId);
    }

    /** 供应商确认收到退货：完成退回，通知采购商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturnReceipt(Long id, Long tenantId, Long userId) {
        Delivery d = lifecycleService.getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        int n = deliveryMapper.confirmReturnReceipt(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        traceSupport.addTrace(id, "return_receipt", "供应商确认收到退货", tenantId, userId, null);

        ProfileInfoDTO supplier = profileClient.getByTenantId(d.getSupplierTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RETURN_RECEIPT")
                .title("供应商已确认收到退货")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】已确认收到退货")
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("供应商确认收到退货: id={}, supplierTenantId={}", id, tenantId);
    }
}
