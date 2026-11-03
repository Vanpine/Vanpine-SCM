package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.dto.PromiseDateDTO;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryPromiseDate;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryPromiseDateMapper;
import cn.greensmartplanet.gsc.delivery.service.DeliveryLifecycleService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryPromiseDateService;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交付承诺交期服务实现
 * <p>
 * 供应商提交 → 采购方确认（写回考核基准）/拒绝。归属校验复用 Lifecycle 基座。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPromiseDateServiceImpl implements DeliveryPromiseDateService {

    private final DeliveryMapper deliveryMapper;
    private final DeliveryPromiseDateMapper promiseDateMapper;
    // 画像归供应商域，Feign 查询（通知文案企业名）
    private final ProfileClient profileClient;
    private final NotificationService notificationService;
    // 生命周期基座：getDeliveryOrThrow 归属校验
    private final DeliveryLifecycleService lifecycleService;

    /** 供应商提交承诺交期：仅未闭环单 + 供应商本人 + 无 pending 时提交，通知采购方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitPromiseDate(Long deliveryId, Long tenantId, Long userId, PromiseDateDTO dto) {
        // 1. 校验交付单存在 + 供应商身份（采购方无提交入口，后端兜底拒绝）
        Delivery d = lifecycleService.getDeliveryOrThrow(deliveryId, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 2. 已闭环单（已验收/已退回）无需再承诺
        if (DeliveryStatus.ACCEPTED.getCode().equals(d.getStatus())
                || DeliveryStatus.RETURNED.getCode().equals(d.getStatus())) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        // 3. 同单已有 pending 承诺时不允许重复提交，先处理旧的
        if (promiseDateMapper.selectPendingByDeliveryId(deliveryId) != null) {
            throw new BizException(ErrorCode.PROMISE_PENDING_EXISTS);
        }
        DeliveryPromiseDate promise = DeliveryPromiseDate.builder()
                .id(IdWorker.getId())
                .deliveryId(deliveryId)
                .promisedDate(dto.getPromisedDate())
                .reason(dto.getReason().trim())
                .status("pending")
                .createBy(userId)
                .build();
        promiseDateMapper.insert(promise);

        // 4. 通知采购方有新的承诺交期待确认
        ProfileInfoDTO supplier = profileClient.getByTenantId(tenantId).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_PROMISE_SUBMIT")
                .title("供应商提交新承诺交期")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】对交付单 "
                        + d.getDeliveryNo() + " 提交承诺交期 " + dto.getPromisedDate()
                        + "（原因：" + dto.getReason().trim() + "），请确认")
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(deliveryId)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("提交承诺交期: deliveryId={}, promisedDate={}, supplierTenantId={}",
                deliveryId, dto.getPromisedDate(), tenantId);
        return promise.getId();
    }

    /** 采购方确认承诺交期：写回考核基准 + 通知供应商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPromiseDate(Long promiseId, Long tenantId, Long userId) {
        DeliveryPromiseDate promise = getPendingPromise(promiseId);
        Delivery d = lifecycleService.getDeliveryOrThrow(promise.getDeliveryId(), tenantId);
        // 仅采购方可确认（供应商无确认入口，后端兜底拒绝）
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 1. pending → accepted（乐观更新，已被处理的返回 0）
        if (promiseDateMapper.confirm(promiseId, userId) == 0) {
            throw new BizException(ErrorCode.PROMISE_STATUS_INVALID);
        }
        // 2. 写回交付单考核基准：OTD/预警/风险扫描 COALESCE 优先取承诺交期
        deliveryMapper.updateConfirmedDeliveryDate(promise.getDeliveryId(), promise.getPromisedDate(), userId);
        // 3. 通知供应商
        notificationService.notify(Notification.builder()
                .type("DELIVERY_PROMISE_CONFIRM")
                .title("承诺交期已被采购方确认")
                .content("交付单 " + d.getDeliveryNo() + " 的承诺交期 " + promise.getPromisedDate() + " 已确认")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(promise.getDeliveryId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("确认承诺交期: promiseId={}, deliveryId={}, promisedDate={}",
                promiseId, promise.getDeliveryId(), promise.getPromisedDate());
    }

    /** 采购方拒绝承诺交期：通知供应商可再提交 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectPromiseDate(Long promiseId, Long tenantId, Long userId) {
        DeliveryPromiseDate promise = getPendingPromise(promiseId);
        Delivery d = lifecycleService.getDeliveryOrThrow(promise.getDeliveryId(), tenantId);
        // 仅采购方可拒绝
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (promiseDateMapper.reject(promiseId, userId) == 0) {
            throw new BizException(ErrorCode.PROMISE_STATUS_INVALID);
        }
        notificationService.notify(Notification.builder()
                .type("DELIVERY_PROMISE_REJECT")
                .title("承诺交期被采购方拒绝")
                .content("交付单 " + d.getDeliveryNo() + " 的承诺交期 " + promise.getPromisedDate() + " 被拒绝，可重新提交")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(promise.getDeliveryId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("拒绝承诺交期: promiseId={}, deliveryId={}", promiseId, promise.getDeliveryId());
    }

    /** 查待确认承诺记录并校验存在（不存在或已处理均拒绝操作） */
    private DeliveryPromiseDate getPendingPromise(Long promiseId) {
        DeliveryPromiseDate promise = promiseDateMapper.selectById(promiseId);
        if (promise == null) {
            throw new BizException(ErrorCode.PROMISE_DATE_NOT_FOUND);
        }
        if (!"pending".equals(promise.getStatus())) {
            throw new BizException(ErrorCode.PROMISE_STATUS_INVALID);
        }
        return promise;
    }
}
