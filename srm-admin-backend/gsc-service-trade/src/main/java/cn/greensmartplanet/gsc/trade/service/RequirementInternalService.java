package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementRecallQueryDTO;

import java.util.List;
import java.util.Map;

/**
 * 采购需求内部契约服务接口 — 供应商域 Feign 调用的契约方法
 * <p>
 * 归口服务间契约：查询不存在返回 null、批量查询缺失不在 map 中（7.1 data=null 语义）。
 *
 * @author gsc
 */
public interface RequirementInternalService {

    /**
     * 按需求ID查基础信息契约 DTO（不存在返回 null）
     */
    RequirementInfoDTO getInfoById(Long id);

    /**
     * 按需求ID查智能匹配字段契约 DTO（不存在返回 null）
     */
    RequirementMatchFieldsDTO getMatchFields(Long id);

    /**
     * 批量按需求ID查基础信息契约 DTO（不存在的不在 map 中）
     */
    Map<Long, RequirementInfoDTO> getInfoByIds(List<Long> ids);

    /**
     * 对接接受后回调：目标供应商数达标则 open → completed（幂等：已 completed 返回 true）
     */
    boolean completeIfTargetReached(Long id, long acceptedCount, int targetSupplierCount);

    /**
     * 找需求 SQL 召回（进行中公开需求，排除自己）
     */
    List<RequirementMatchFieldsDTO> recallOpenForMatch(RequirementRecallQueryDTO query);
}
