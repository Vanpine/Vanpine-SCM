package cn.greensmartplanet.gsc.delivery.service;

import java.util.Map;

/**
 * 交付风险查询服务
 * <p>
 * 纯读接口：风险分页 / 详情 / 按交付单取未关闭风险。
 * 从 DeliveryRiskServiceImpl 拆出，关联数据（合同/企业/交付单号）全部 Feign 批量回填。
 *
 * @author gsc
 */
public interface DeliveryRiskQueryService {

    /** 分页查风险（采购/供应双视角），批量回填合同/企业/交付单号 */
    Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                             String riskStatus, String riskLevel, String role);

    /** 驾驶舱分页：按交付单聚合活跃风险（一行一交付单，等级取最高/分取最高） */
    Map<String, Object> listDashboard(Long tenantId, int pageNum, int pageSize,
                                      String riskStatus, String riskLevel, String role);

    /** 风险详情：回填合同/企业/交付单信息 + 物流事件时间线 + 因子轨迹 + 8D 报告 */
    Map<String, Object> detail(Long id, Long tenantId);

    /** 按交付单查未关闭风险（物流追踪页风险联动）：无未关闭风险返回 data.risk=null */
    Map<String, Object> getActiveByDelivery(Long deliveryId, Long tenantId);
}
