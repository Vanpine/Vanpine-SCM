package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.delivery.projection.CreatableContractRow;

import java.util.List;
import java.util.Map;

/**
 * 交付单查询服务
 * <p>
 * 纯读接口：分页列表 / 详情 / 可建单合同 / 发货排期 / 进行中校验。
 * 从 DeliveryServiceImpl 拆出，仅依赖 Mapper + Feign 批量查询，无写操作。
 *
 * @author gsc
 */
public interface DeliveryQueryService {

    /** 分页列表（采购方/供应方） */
    Map<String, Object> list(Long tenantId, int pageNum, int pageSize, String status, String keyword, String role);

    /** 详情（含履约时间线） */
    Map<String, Object> getDetail(Long id, Long tenantId);

    /** 供应方可建交付单的合同列表（已签署且尚有未分配数量） */
    List<CreatableContractRow> listCreatableContracts(Long tenantId);

    /** 供应商发货排期列表（分批计划 + 发货状态） */
    Map<String, Object> listShipmentPlan(Long tenantId, int pageNum, int pageSize, String keyword, String status);

    /** 合同是否存在进行中（非 accepted）交付单（终止保护） */
    boolean hasActiveDelivery(Long contractId);
}
