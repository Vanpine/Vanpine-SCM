package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.api.supplier.dto.MyConnectionStatusDTO;
import cn.greensmartplanet.gsc.supplier.vo.ConnectionVO;

import java.util.List;
import java.util.Map;

/**
 * 对接记录查询服务接口 — 列表 / 我的对接详情 / 内部契约
 * <p>
 * 只读查询，无 @Transactional。VO 拼装（需求标题/双方企业名/联系方式）采用批量 Feign，消 N+1。
 *
 * @author gsc
 */
public interface ConnectionQueryService {

    /** 分页查我的对接（买方视角=我发起的，供应方视角=发给我的），支持关键词、角色、需求过滤 */
    Map<String, Object> listMyConnections(Long tenantId, int pageNum, int pageSize,
                                          String status, String keyword, String role, Long requirementId);

    /** 按需求ID查当前用户对该需求的对接详情（含联系方式，仅accepted可见） */
    ConnectionVO getMyConnectionByRequirement(Long requirementId, Long tenantId);

    // ==================== 内部契约（交易域 Feign 调用） ====================

    /** 按需求ID批量查已接受对接数（requirementId → count，无记录的返回 0） */
    Map<Long, Long> countAcceptedByRequirementIds(Long buyerTenantId, List<Long> requirementIds);

    /** 查某租户在多个需求下的对接状态（requirementId → DTO；无记录返回空 map） */
    Map<Long, MyConnectionStatusDTO> getMyConnectionStatusByRequirementIds(Long tenantId, List<Long> requirementIds);
}
