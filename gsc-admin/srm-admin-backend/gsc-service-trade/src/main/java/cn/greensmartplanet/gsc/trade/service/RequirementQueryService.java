package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.vo.RequirementVO;

import java.util.Map;

/**
 * 采购需求查询服务接口 — 我的需求 / 需求详情 / 需求广场
 * <p>
 * 只读查询归口本服务，与写操作（生命周期）解耦。
 *
 * @author gsc
 */
public interface RequirementQueryService {

    /**
     * 分页查我的需求（先扫描过期），批量回填对接数
     */
    Map<String, Object> listMyRequirements(Long tenantId, int pageNum, int pageSize, String status, String keyword);

    /**
     * 需求详情（非发布方且非公开需求拒绝访问）
     */
    RequirementVO getDetail(Long id, Long tenantId);

    /**
     * 需求广场：跨租户查 open 需求（排除自己），附发布方企业名、对接数、我的对接状态
     */
    Map<String, Object> listOpenRequirements(Long excludeTenantId, String keyword, int pageNum, int pageSize);
}
