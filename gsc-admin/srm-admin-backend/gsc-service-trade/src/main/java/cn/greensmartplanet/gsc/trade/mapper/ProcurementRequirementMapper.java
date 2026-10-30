package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购需求 Mapper
 * <p>
 * 按需求类型区分两类查询：
 * 1. 我的需求（按 tenant_id 隔离，采购方管理自己的需求）
 * 2. 公开需求（跨租户，用于需求广场和智能匹配）
 *
 * @author gsc
 */
public interface ProcurementRequirementMapper extends BaseMapper<ProcurementRequirement> {

    /** 分页查我的需求（按 tenant_id 隔离），支持按状态和关键词筛选 */
    List<ProcurementRequirement> selectMyPage(@Param("tenantId") Long tenantId,
                                              @Param("status") String status,
                                              @Param("keyword") String keyword,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 统计我的需求总数 */
    long countMy(@Param("tenantId") Long tenantId,
                 @Param("status") String status,
                 @Param("keyword") String keyword);

    /** 按ID查单条（含 is_deleted=0） */
    ProcurementRequirement selectById(@Param("id") Long id);

    /** 插入需求 */
    int insert(ProcurementRequirement requirement);

    /** 编辑需求（仅 open 状态可编辑，仅发布方可操作） */
    int updateById(@Param("id") Long id,
                   @Param("tenantId") Long tenantId,
                   @Param("req") ProcurementRequirement req);

    /** 关闭需求（open/completed → closed），仅发布方可操作 */
    int closeById(@Param("id") Long id,
                  @Param("tenantId") Long tenantId);

    /** 更新需求状态（如 open → completed） */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);

    /** 重新发布（closed/expired/completed → open），仅发布方可操作 */
    int republishById(@Param("id") Long id,
                      @Param("tenantId") Long tenantId);

    /** 扫描过期需求：将 open 状态且 deadline < NOW() 的改为 expired */
    int updateExpired();

    /** 跨租户查所有 open 状态需求（排除指定租户，支持关键词搜索），按创建时间倒序 */
    List<ProcurementRequirement> selectOpenListExcludeTenant(@Param("excludeTenantId") Long excludeTenantId,
                                                              @Param("keyword") String keyword,
                                                              @Param("offset") int offset,
                                                              @Param("limit") int limit);

    /** 统计公开需求总数（排除指定租户，支持关键词搜索） */
    long countOpenExcludeTenant(@Param("excludeTenantId") Long excludeTenantId,
                                @Param("keyword") String keyword);

    /**
     * 找需求召回：进行中公开需求。
     * categoryTokens / region 为空则不过滤该项；地域空或「不限」视为对所有供应商开放。
     */
    List<ProcurementRequirement> selectOpenForRecall(@Param("excludeTenantId") Long excludeTenantId,
                                                     @Param("categoryTokens") List<String> categoryTokens,
                                                     @Param("region") String region,
                                                     @Param("limit") int limit);
}
