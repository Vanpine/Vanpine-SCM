package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.delivery.dto.CorrectTrackingDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReceiveDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ShipDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;

/**
 * 交付单生命周期服务（主状态机）
 * <p>
 * 从 DeliveryServiceImpl 拆出：发货/收货/验收/运单更正 + 创建/删除 + 按分批计划发货。
 * 与退货状态机（DeliveryReturnService）、承诺交期（DeliveryPromiseDateService）通过
 * getDeliveryOrThrow 归属校验 + DeliveryAssessmentService 评估协作者解耦。
 *
 * @author gsc
 */
public interface DeliveryLifecycleService {

    /** 供应商发货（pending_shipment/returned → shipped） */
    void ship(Long id, Long tenantId, Long userId, ShipDeliveryDTO dto);

    /** 采购商收货（shipped → received） */
    void receive(Long id, Long tenantId, Long userId, ReceiveDeliveryDTO dto);

    /** 采购商验收合格（received → accepted，聚合判断合同完成） */
    void accept(Long id, Long tenantId, Long userId, String remark);

    /** 供应商更正运单（仅 shipped，写时间线） */
    void correctTracking(Long id, Long tenantId, Long userId, CorrectTrackingDTO dto);

    /** 合同签署后自动生成待发货交付单（幂等）——contract 为 Feign 契约 DTO（MQ 消费者重建场景） */
    void createFromContract(ContractInfoDTO contract);

    /** 供应方手动创建交付单（主动分批），返回新建交付单ID */
    Long createManually(Long tenantId, Long userId, CreateDeliveryDTO dto);

    /** 供应方删除待发货交付单，数量退回合同未分配额度 */
    void delete(Long id, Long tenantId, Long userId);

    /** 供应商按分批计划发货：创建发货单 + 直接发货（一步到位） */
    void shipFromBatchPlan(Long tenantId, Long userId, Long contractId, Long batchPlanId, ShipDeliveryDTO dto);

    /**
     * 查交付单并校验归属（采购方或供应方）——生命周期基座校验
     * <p>供退货状态机 / 承诺交期服务复用，避免各服务重复实现归属校验。
     */
    Delivery getDeliveryOrThrow(Long id, Long tenantId);
}
