package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.delivery.dto.Create8dDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateLogisticsEventDTO;
import cn.greensmartplanet.gsc.delivery.dto.Submit8dDTO;

/**
 * 交付风险生命周期服务
 * <p>
 * 从 DeliveryRiskServiceImpl 拆出：物流事件录入 + 风险扫描识别 + 8D 整改流转。
 * 查询/详情/按交付单联动移入 {@link DeliveryRiskQueryService}。
 *
 * @author gsc
 */
public interface DeliveryRiskLifecycleService {

    /**
     * 录入物流事件（平台内人工填报），落库后触发一次风险识别
     */
    void recordEvent(Long tenantId, Long userId, CreateLogisticsEventDTO dto);

    /**
     * 定时扫描：按合同规则识别/升级未闭环交付单的风险（批量预取关联数据，无 N+1）
     */
    void scanRisks();

    /**
     * 采购方发起 8D 整改（仅未关闭风险，一风险一张）
     */
    Long create8d(Long riskId, Long tenantId, Long userId, Create8dDTO dto);

    /**
     * 供应商提交 8D 报告（D1-D8 全必填）
     */
    void submit8d(Long reportId, Long tenantId, Submit8dDTO dto);

    /**
     * 采购方审核通过 8D
     */
    void approve8d(Long reportId, Long tenantId, Long userId);

    /**
     * 采购方打回 8D（打回原因必填）
     */
    void reject8d(Long reportId, Long tenantId, Long userId, String reason);
}
