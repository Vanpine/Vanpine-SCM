package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.dto.CorrectTrackingDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReceiveDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ShipDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.entity.ContractBatchPlan;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.enums.LogisticsEventType;
import cn.greensmartplanet.gsc.delivery.mapper.ContractBatchPlanMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryLogisticsEventMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.service.DeliveryLifecycleService;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import cn.greensmartplanet.gsc.delivery.support.DeliveryAssessmentService;
import cn.greensmartplanet.gsc.delivery.support.DeliveryNoGenerator;
import cn.greensmartplanet.gsc.delivery.support.DeliveryTraceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 交付单生命周期服务实现（主状态机）
 * <p>
 * 发货/收货/验收/运单更正 + 合同签署建单/手动建单/删除/分批计划发货。
 * 验收终态评估（违约金/合同完成聚合/履约表现回写）委托 {@link DeliveryAssessmentService}；
 * 轨迹落库委托 {@link DeliveryTraceSupport}；编号生成委托 {@link DeliveryNoGenerator}。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryLifecycleServiceImpl implements DeliveryLifecycleService {

    private final DeliveryMapper deliveryMapper;
    // ⚠️ 迁移期桥接：contract_batch_plan 表归属交易域，3 条跨域 JOIN SQL（排期/可建合同/未发批次计数）
    // 暂留本服务共享单库直查；阶段 8 拆库前需把分批计划整体迁回 trade 或走 Feign 聚合。
    private final ContractBatchPlanMapper contractBatchPlanMapper;
    private final DeliveryLogisticsEventMapper logisticsEventMapper;
    // 合同信息归交易域，Feign 查询（发货校验/建单/合同完成回写）
    private final ContractClient contractClient;
    // 画像归供应商域，Feign 查询（通知文案企业名）
    private final ProfileClient profileClient;
    private final NotificationService notificationService;
    // 交付编号生成（Redis 自增序号，创建/拆批/补发复用）
    private final DeliveryNoGenerator deliveryNoGenerator;
    // 交付轨迹落库（主状态机/退货/承诺交期共用）
    private final DeliveryTraceSupport traceSupport;
    // 交付终态评估（违约金/合同完成/履约表现）
    private final DeliveryAssessmentService assessmentService;

    /** 供应商发货：校验数量、按需拆批、记录物流信息与轨迹，通知采购方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id, Long tenantId, Long userId, ShipDeliveryDTO dto) {
        // 1. 校验供应商身份
        Delivery d = getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 2. 数量校验：不得超本批应发，累计不得超合同约定
        int shipQty = dto.getShipQty();
        Integer batchQty = d.getQuantity();
        boolean isReship = d.getReshipFrom() != null;
        // 补发批强制整批发货（禁止部分发货，否则拆出计入守恒的假普通批）
        if (isReship && batchQty != null && shipQty != batchQty) {
            throw new BizException(ErrorCode.DELIVERY_RESHIP_QTY_MUST_EQUAL);
        }
        ContractInfoDTO contract = contractClient.getContractById(d.getContractId()).getData();
        if (batchQty != null && shipQty > batchQty) {
            throw new BizException(ErrorCode.DELIVERY_SHIP_QTY_EXCEED);
        }
        // 补发免费不占合同数量，跳过累计发货量校验（原批已发满合同全量）
        if (!isReship && contract != null && contract.getQuantity() != null) {
            // 排除本批的历史发货量（退回重发时会被本次覆盖），再叠加本次
            int shippedOther = deliveryMapper.sumShippedQtyByContract(d.getContractId(), id);
            if (shippedOther + shipQty > contract.getQuantity()) {
                throw new BizException(ErrorCode.DELIVERY_SHIP_QTY_TOTAL_EXCEED);
            }
        }
        // 3. 实发少于本批应发：首次发货拆出下一批承接剩余；重新发货补发即最终，仅收窄本批不拆批
        if (batchQty != null && shipQty < batchQty) {
            if (DeliveryStatus.RETURNED.getCode().equals(d.getStatus())) {
                BigDecimal currAmount = null;
                if (d.getAmount() != null && batchQty > 0) {
                    currAmount = d.getAmount()
                            .divide(BigDecimal.valueOf(batchQty), 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(shipQty))
                            .setScale(4, RoundingMode.HALF_UP);
                }
                deliveryMapper.shrinkBatch(d.getId(), shipQty, currAmount, userId);
            } else {
                splitBatch(d, shipQty, batchQty, userId);
            }
        }
        // 4. 更新发货信息 + 记录轨迹
        int n = deliveryMapper.ship(id, tenantId, dto.getShipCompany().trim(), dto.getShipTrackingNo().trim(),
                shipQty, dto.getShipDate(), dto.getShipAttachmentKey().trim(), userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        traceSupport.addTrace(id, "ship", "供应商发货（" + dto.getShipCompany().trim() + " / " + dto.getShipTrackingNo().trim()
                        + "，数量 " + shipQty + "）",
                tenantId, userId, null);

        // 5. 通知采购方
        ProfileInfoDTO supplier = profileClient.getByTenantId(d.getSupplierTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_SHIP")
                .title("交付已发货")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】已发货"
                        + (contract != null ? "（合同《" + contract.getTitle() + "》）" : "")
                        + "，第 " + (d.getBatchNo() != null ? d.getBatchNo() : 1) + " 批 " + shipQty + " 件"
                        + "，物流：" + dto.getShipCompany() + " " + dto.getShipTrackingNo())
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("交付单已发货: id={}, supplierTenantId={}", id, tenantId);
    }

    /** 采购方收货：数量不符须填差异说明，通知供应商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long id, Long tenantId, Long userId, ReceiveDeliveryDTO dto) {
        // 1. 校验采购方身份 + 数量差异说明
        Delivery d = getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        Integer expectedQty = d.getShipQty() != null ? d.getShipQty() : d.getQuantity();
        if (expectedQty != null && !expectedQty.equals(dto.getReceiveQty())) {
            if (dto.getReceiveDiffRemark() == null || dto.getReceiveDiffRemark().trim().isEmpty()) {
                throw new BizException(ErrorCode.DELIVERY_DIFF_REMARK_REQUIRED);
            }
        }
        String diffRemark = dto.getReceiveDiffRemark() != null ? dto.getReceiveDiffRemark().trim() : null;
        if (diffRemark != null && diffRemark.isEmpty()) {
            diffRemark = null;
        }
        // 2. 更新收货 + 记录轨迹
        int n = deliveryMapper.receive(id, tenantId, dto.getReceiveQty(), diffRemark,
                dto.getReceiveAttachmentKey().trim(), userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        String desc = "采购商已确认收货（实收 " + dto.getReceiveQty() + "）";
        traceSupport.addTrace(id, "receive", desc, tenantId, userId, diffRemark);

        // 3. 通知供应商
        ProfileInfoDTO buyer = profileClient.getByTenantId(d.getBuyerTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_RECEIVE")
                .title("交付已收货")
                .content("【" + (buyer != null ? buyer.getCompanyName() : "采购方") + "】已确认收货")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("交付单已收货: id={}, buyerTenantId={}", id, tenantId);
    }

    /** 采购方验收合格：聚合判断合同下所有交付单均验收才置合同 completed */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, Long tenantId, Long userId, String remark) {
        // 1. 校验采购方身份并更新验收
        Delivery d = getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 验收前置：物流节点必须先显示"到达/签收"，否则不允许验收
        if (logisticsEventMapper.countByDeliveryAndType(id, LogisticsEventType.ARRIVED_DELIVERED.getCode()) == 0) {
            throw new BizException(ErrorCode.DELIVERY_ACCEPT_NEED_ARRIVED);
        }
        int n = deliveryMapper.accept(id, tenantId, remark, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        traceSupport.addTrace(id, "accept", "采购商验收合格", tenantId, userId, remark);

        // 验收完成：计算违约金快照（按合同条款，延迟=收货-考核基准-容差）
        assessmentService.calcPenalty(d);

        // 聚合判断：该合同所有交付单均验收合格才完成合同（一期单交付单退化为唯一一张）
        boolean allDone = assessmentService.completeContractIfAllDelivered(d.getContractId(), tenantId, userId);
        if (!allDone) {
            // 分期交付：部分验收，仅通知供应商本次验收合格
            ProfileInfoDTO buyer = profileClient.getByTenantId(d.getBuyerTenantId()).getData();
            notificationService.notify(Notification.builder()
                    .type("DELIVERY_ACCEPT")
                    .title("交付验收合格")
                    .content("【" + (buyer != null ? buyer.getCompanyName() : "采购方") + "】已验收合格")
                    .receiverTenantId(d.getSupplierTenantId())
                    .relatedType("delivery")
                    .relatedId(id)
                    .fromTenantId(tenantId)
                    .fromUserId(userId)
                    .build());
        }
        log.info("交付单验收合格: id={}, buyerTenantId={}", id, tenantId);
        assessmentService.recalcSupplierPerformance(d.getSupplierTenantId());
    }

    /** 供应商更正运单号：须有变化，记录轨迹并通知采购方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void correctTracking(Long id, Long tenantId, Long userId, CorrectTrackingDTO dto) {
        // 1. 校验供应商身份 + 运单有变化
        Delivery d = getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        String newCompany = dto.getShipCompany().trim();
        String newTrackingNo = dto.getShipTrackingNo().trim();
        if (newCompany.equals(d.getShipCompany()) && newTrackingNo.equals(d.getShipTrackingNo())) {
            throw new BizException(ErrorCode.DELIVERY_TRACKING_UNCHANGED);
        }
        // 2. 更新运单 + 记录轨迹
        int n = deliveryMapper.correctTracking(id, tenantId, newCompany, newTrackingNo, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        String oldInfo = (d.getShipCompany() != null ? d.getShipCompany() : "-")
                + " / " + (d.getShipTrackingNo() != null ? d.getShipTrackingNo() : "-");
        String newInfo = newCompany + " / " + newTrackingNo;
        traceSupport.addTrace(id, "correct_tracking", "供应商更正运单（" + oldInfo + " → " + newInfo + "）",
                tenantId, userId, oldInfo + " → " + newInfo);

        // 3. 通知采购方
        ProfileInfoDTO supplier = profileClient.getByTenantId(d.getSupplierTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_TRACKING")
                .title("运单信息已更正")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】已更正运单：" + newInfo)
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(id)
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("交付单运单已更正: id={}, supplierTenantId={}, {} → {}", id, tenantId, oldInfo, newInfo);
    }

    /** 合同签署后自动生成首批待发货交付单（幂等：已有则不重复）——contract 为 Feign 契约 DTO */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFromContract(ContractInfoDTO contract) {
        // 1. 仅签署状态才生成 + 幂等校验（已有交付单则不重复）
        if (contract == null || !ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
            return;
        }
        if (deliveryMapper.countByContract(contract.getId()) > 0) {
            return;
        }
        // 2. 生成第 1 批待发货交付单（数量取合同全量，发货不足时再拆批）+ 记录轨迹 + 通知供应商
        Delivery d = Delivery.builder()
                .deliveryNo(deliveryNoGenerator.generate())
                .contractId(contract.getId())
                .inquiryId(contract.getInquiryId())
                .batchNo(1)
                .buyerTenantId(contract.getBuyerTenantId())
                .supplierTenantId(contract.getSupplierTenantId())
                .quantity(contract.getQuantity())
                .amount(contract.getAmount())
                .currency(contract.getCurrency() != null ? contract.getCurrency() : "CNY")
                .deliveryTerms(contract.getDeliveryTerms())
                .deliveryPlace(contract.getDeliveryPlace())
                .deliveryDate(contract.getDeliveryDate())
                .status(DeliveryStatus.PENDING_SHIPMENT.getCode())
                .createBy(contract.getCreateBy())
                .build();
        deliveryMapper.insert(d);
        traceSupport.addTrace(d.getId(), "create", "合同签署，自动生成交付单", null, null, null);

        ProfileInfoDTO buyer = profileClient.getByTenantId(contract.getBuyerTenantId()).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_CREATE")
                .title("合同已签署，待发货")
                .content("【" + (buyer != null ? buyer.getCompanyName() : "采购方") + "】的合同《" + contract.getTitle()
                        + "》已签署完成，请在交付管理中发货")
                .receiverTenantId(contract.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(contract.getBuyerTenantId())
                .fromUserId(contract.getCreateBy())
                .build());
        log.info("交付单自动生成: id={}, deliveryNo={}, contractId={}", d.getId(), d.getDeliveryNo(), contract.getId());
    }

    /**
     * 供应方手动创建交付单（提前排产分批）
     * <p>
     * 额度优先取合同未分配数量；不够时从数量最大的待发货批次让出，
     * 该批至少保留 1 件，避免出现数量为 0 的空批次。全程不改变合同总量。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createManually(Long tenantId, Long userId, CreateDeliveryDTO dto) {
        // 1. 校验合同存在、已签署、当前租户确为供应方（合同归交易域，Feign 查询）
        ContractInfoDTO contract = contractClient.getContractById(dto.getContractId()).getData();
        if (contract == null) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }
        if (!ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.DELIVERY_CONTRACT_NOT_SIGNED);
        }
        if (!tenantId.equals(contract.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }

        // 2. 算额度：未分配数量 + 待发货批次可让出的数量（源批留 1）
        int qty = dto.getQuantity();
        int allocated = deliveryMapper.sumQuantityByContract(contract.getId());
        int unallocated = contract.getQuantity() != null
                ? Math.max(0, contract.getQuantity() - allocated) : Integer.MAX_VALUE;
        Delivery source = null;
        int needDeduct = 0;
        if (qty > unallocated) {
            source = deliveryMapper.selectLargestPendingByContract(contract.getId());
            needDeduct = qty - unallocated;
            if (source == null || source.getQuantity() == null
                    || needDeduct > source.getQuantity() - 1) {
                throw new BizException(ErrorCode.DELIVERY_CREATE_QTY_EXCEED);
            }
        }

        // 3. 需要让额度时先收窄源批次，金额按源批单价同步扣减
        if (source != null) {
            int keepQty = source.getQuantity() - needDeduct;
            BigDecimal keepAmount = null;
            if (source.getAmount() != null) {
                keepAmount = source.getAmount()
                        .divide(BigDecimal.valueOf(source.getQuantity()), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(keepQty))
                        .setScale(4, RoundingMode.HALF_UP);
            }
            deliveryMapper.shrinkBatch(source.getId(), keepQty, keepAmount, userId);
        }
        // 收窄后按最新已分配量分摊本批金额，避免把让出的部分重复计入
        int allocatedNow = deliveryMapper.sumQuantityByContract(contract.getId());
        BigDecimal amount = calcBatchAmount(contract, allocatedNow, qty);

        // 4. 建单，批次号接在现有批次之后
        int batchNo = deliveryMapper.selectMaxBatchNo(contract.getId()) + 1;
        Delivery d = Delivery.builder()
                .deliveryNo(deliveryNoGenerator.generate())
                .contractId(contract.getId())
                .inquiryId(contract.getInquiryId())
                .batchNo(batchNo)
                .buyerTenantId(contract.getBuyerTenantId())
                .supplierTenantId(contract.getSupplierTenantId())
                .quantity(qty)
                .amount(amount)
                .currency(contract.getCurrency() != null ? contract.getCurrency() : "CNY")
                .deliveryTerms(contract.getDeliveryTerms())
                .deliveryPlace(contract.getDeliveryPlace())
                .deliveryDate(contract.getDeliveryDate())
                .status(DeliveryStatus.PENDING_SHIPMENT.getCode())
                .createBy(userId)
                .build();
        deliveryMapper.insert(d);
        traceSupport.addTrace(d.getId(), "create", "供应商创建第 " + batchNo + " 批交付单（应发 " + qty + "）",
                tenantId, userId, null);
        // 源批次也留痕，双方都能看到额度是从哪一批划走的
        if (source != null) {
            traceSupport.addTrace(source.getId(), "split",
                    "让出 " + needDeduct + " 件给第 " + batchNo + " 批，本批应发调整为 "
                            + (source.getQuantity() - needDeduct),
                    tenantId, userId, null);
        }

        // 5. 通知采购方有新批次待履约
        ProfileInfoDTO supplier = profileClient.getByTenantId(tenantId).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_CREATE")
                .title("新增交付批次")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】为合同《"
                        + contract.getTitle() + "》创建了第 " + batchNo + " 批交付单，应发 " + qty)
                .receiverTenantId(contract.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("手动创建交付单: id={}, deliveryNo={}, contractId={}, batchNo={}, qty={}, 从批次{}让出{}",
                d.getId(), d.getDeliveryNo(), contract.getId(), batchNo, qty,
                source != null ? source.getId() : null, needDeduct);
        return d.getId();
    }

    /**
     * 供应方删除待发货交付单
     * <p>
     * 只允许删尚未发出的批次：已发货起就有物流与收货凭证，属于履约痕迹，不可抹除。
     * 采用逻辑删除，该批数量随即退回合同未分配额度，可重新排产建批。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long tenantId, Long userId) {
        // 1. 校验归属：必须是本单供应方
        Delivery d = getDeliveryOrThrow(id, tenantId);
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.DELIVERY_DELETE_NOT_SUPPLIER);
        }
        // 2. 仅待发货可删，returned 已有实际发货记录不在此列
        if (!DeliveryStatus.PENDING_SHIPMENT.getCode().equals(d.getStatus())) {
            throw new BizException(ErrorCode.DELIVERY_DELETE_STATUS_INVALID);
        }
        // 3. 先留痕再删，时间线随交付单一并保留以备追溯
        traceSupport.addTrace(d.getId(), "delete",
                "供应商删除第 " + (d.getBatchNo() != null ? d.getBatchNo() : 1) + " 批交付单（应发 "
                        + d.getQuantity() + "），数量退回合同未分配额度",
                tenantId, userId, null);
        deliveryMapper.deleteById(id, userId);

        // 4. 通知采购方，避免其按原批次安排收货
        ContractInfoDTO contract = contractClient.getContractById(d.getContractId()).getData();
        ProfileInfoDTO supplier = profileClient.getByTenantId(tenantId).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_DELETE")
                .title("交付批次已删除")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】删除了合同《"
                        + (contract != null ? contract.getTitle() : "") + "》的第 "
                        + (d.getBatchNo() != null ? d.getBatchNo() : 1) + " 批交付单（应发 " + d.getQuantity() + "）")
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("contract")
                .relatedId(d.getContractId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("删除交付单: id={}, deliveryNo={}, contractId={}, batchNo={}, qty={}",
                d.getId(), d.getDeliveryNo(), d.getContractId(), d.getBatchNo(), d.getQuantity());
    }

    /** 供应商按分批计划发货：创建发货单 + 直接发货（一步到位） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipFromBatchPlan(Long tenantId, Long userId, Long contractId,
                                  Long batchPlanId, ShipDeliveryDTO dto) {
        // 1. 校验合同存在、已签署、当前租户是供应方（合同归交易域，Feign 查询）
        ContractInfoDTO contract = contractClient.getContractById(contractId).getData();
        if (contract == null) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }
        if (!ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.DELIVERY_CONTRACT_NOT_SIGNED);
        }
        if (!tenantId.equals(contract.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }

        // 2. 读分批计划
        ContractBatchPlan plan = contractBatchPlanMapper.selectById(batchPlanId);
        if (plan == null || !contractId.equals(plan.getContractId())) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }

        // 3. 防重：该批次已有发货单则不允许重复发货
        if (deliveryMapper.countByContractAndBatchNo(contractId, plan.getBatchNo()) > 0) {
            throw new BizException(ErrorCode.DELIVERY_BATCH_ALREADY_SHIPPED);
        }

        // 4. 数量校验：必须等于本批应发数量（不允许部分发货）
        int shipQty = dto.getShipQty();
        if (plan.getQuantity() != null && shipQty != plan.getQuantity()) {
            throw new BizException(ErrorCode.DELIVERY_SHIP_QTY_MUST_EQUAL_BATCH);
        }

        // 5. 金额按单价分摊
        BigDecimal batchAmount = null;
        if (contract.getAmount() != null && contract.getQuantity() != null && contract.getQuantity() > 0) {
            batchAmount = contract.getAmount()
                    .divide(BigDecimal.valueOf(contract.getQuantity()), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(plan.getQuantity()))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        // 6. 创建发货单（直接 shipped 状态）
        Delivery d = Delivery.builder()
                .deliveryNo(deliveryNoGenerator.generate())
                .contractId(contractId)
                .inquiryId(contract.getInquiryId())
                .batchNo(plan.getBatchNo())
                .buyerTenantId(contract.getBuyerTenantId())
                .supplierTenantId(tenantId)
                .quantity(plan.getQuantity())
                .amount(batchAmount)
                .currency(contract.getCurrency() != null ? contract.getCurrency() : "CNY")
                .deliveryTerms(contract.getDeliveryTerms())
                .deliveryPlace(contract.getDeliveryPlace())
                .deliveryDate(plan.getDeliveryDate())
                .status(DeliveryStatus.SHIPPED.getCode())
                .shipCompany(dto.getShipCompany().trim())
                .shipTrackingNo(dto.getShipTrackingNo().trim())
                .shipQty(shipQty)
                .shipDate(dto.getShipDate())
                .shipAttachmentKey(dto.getShipAttachmentKey().trim())
                .shipTime(LocalDateTime.now())
                .createBy(userId)
                .build();
        deliveryMapper.insert(d);

        // 7. 记录轨迹 + 通知采购方
        traceSupport.addTrace(d.getId(), "ship", "供应商按第 " + plan.getBatchNo() + " 批计划发货（"
                + dto.getShipCompany().trim() + " / " + dto.getShipTrackingNo().trim()
                + "，数量 " + shipQty + "）", tenantId, userId, null);

        ProfileInfoDTO supplier = profileClient.getByTenantId(tenantId).getData();
        notificationService.notify(Notification.builder()
                .type("DELIVERY_SHIP")
                .title("交付已发货")
                .content("【" + (supplier != null ? supplier.getCompanyName() : "供应商") + "】已发货"
                        + "（合同《" + contract.getTitle() + "》第 " + plan.getBatchNo() + " 批）"
                        + "，数量 " + shipQty + "，物流：" + dto.getShipCompany() + " " + dto.getShipTrackingNo())
                .receiverTenantId(contract.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("按分批计划发货: deliveryId={}, contractId={}, batchNo={}, qty={}",
                d.getId(), contractId, plan.getBatchNo(), shipQty);
    }

    /** 查交付单并校验归属（采购方或供应方）——生命周期基座校验，供退货/承诺交期复用 */
    @Override
    public Delivery getDeliveryOrThrow(Long id, Long tenantId) {
        Delivery d = deliveryMapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (!tenantId.equals(d.getBuyerTenantId()) && !tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return d;
    }

    // ==================== 私有工具 ====================

    /**
     * 拆分批次：本批收窄为实发数量，剩余数量与金额转入新建的下一批
     * <p>
     * 金额按本批单价分摊，新批取「原金额 - 本批金额」的减法结果而非各自四舍五入，
     * 保证同合同各批金额之和恒等于合同总额，不产生累计误差。
     */
    private void splitBatch(Delivery d, int shipQty, int batchQty, Long userId) {
        // 1. 按单价分摊金额：本批四舍五入取 4 位，剩余额用减法兜底
        BigDecimal currAmount = null;
        BigDecimal nextAmount = null;
        if (d.getAmount() != null && batchQty > 0) {
            BigDecimal unitPrice = d.getAmount()
                    .divide(BigDecimal.valueOf(batchQty), 6, RoundingMode.HALF_UP);
            currAmount = unitPrice.multiply(BigDecimal.valueOf(shipQty))
                    .setScale(4, RoundingMode.HALF_UP);
            nextAmount = d.getAmount().subtract(currAmount);
        }
        // 2. 收窄本批数量与金额
        deliveryMapper.shrinkBatch(d.getId(), shipQty, currAmount, userId);

        // 3. 新建下一批承接剩余数量，状态回到待发货
        int remainQty = batchQty - shipQty;
        int nextBatchNo = deliveryMapper.selectMaxBatchNo(d.getContractId()) + 1;
        Delivery next = Delivery.builder()
                .deliveryNo(deliveryNoGenerator.generate())
                .contractId(d.getContractId())
                .inquiryId(d.getInquiryId())
                .batchNo(nextBatchNo)
                .buyerTenantId(d.getBuyerTenantId())
                .supplierTenantId(d.getSupplierTenantId())
                .quantity(remainQty)
                .amount(nextAmount)
                .currency(d.getCurrency())
                .deliveryTerms(d.getDeliveryTerms())
                .deliveryPlace(d.getDeliveryPlace())
                .deliveryDate(d.getDeliveryDate())
                .status(DeliveryStatus.PENDING_SHIPMENT.getCode())
                .createBy(userId)
                .build();
        deliveryMapper.insert(next);

        // 4. 两批各记一条轨迹，保证双方时间线都能追溯到这次拆分
        traceSupport.addTrace(d.getId(), "split",
                "本批实发 " + shipQty + "，剩余 " + remainQty + " 转入第 " + nextBatchNo + " 批",
                d.getSupplierTenantId(), userId, null);
        traceSupport.addTrace(next.getId(), "create",
                "上一批部分发货，自动生成第 " + nextBatchNo + " 批（应发 " + remainQty + "）",
                null, null, null);
        log.info("交付单分批: contractId={}, 原批次id={}, 实发={}, 剩余={}, 新批次no={}, 新批次id={}",
                d.getContractId(), d.getId(), shipQty, remainQty, nextBatchNo, next.getId());
    }

    /**
     * 计算手动建单时的本批金额
     * <p>
     * 非收尾批按合同单价乘本批数量；收尾批（本批建完即分配满）改用
     * 「合同总额 - 已建各批金额之和」，保证各批相加恒等于合同总额，不留尾差。
     */
    private BigDecimal calcBatchAmount(ContractInfoDTO contract, int allocated, int qty) {
        if (contract.getAmount() == null || contract.getQuantity() == null || contract.getQuantity() <= 0) {
            return null;
        }
        if (allocated + qty == contract.getQuantity()) {
            BigDecimal used = deliveryMapper.sumAmountByContract(contract.getId());
            return contract.getAmount().subtract(used != null ? used : BigDecimal.ZERO);
        }
        return contract.getAmount()
                .divide(BigDecimal.valueOf(contract.getQuantity()), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(qty))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
