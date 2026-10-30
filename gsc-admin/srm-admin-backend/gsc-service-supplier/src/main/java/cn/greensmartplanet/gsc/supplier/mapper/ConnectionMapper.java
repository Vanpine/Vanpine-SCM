package cn.greensmartplanet.gsc.supplier.mapper;

import cn.greensmartplanet.gsc.supplier.entity.Connection;
import cn.greensmartplanet.gsc.supplier.projection.ConnectionCountRow;
import cn.greensmartplanet.gsc.supplier.projection.SupplierConnectionStatusRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对接记录 Mapper
 *
 * @author gsc
 */
public interface ConnectionMapper extends BaseMapper<Connection> {

    /**
     * 统一查我的对接（合并买卖双方，支持关键词+角色筛选+需求过滤），JOIN 需求和企业画像表
     */
    List<Connection> selectMyConnections(@Param("tenantId") Long tenantId,
                                         @Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("role") String role,
                                         @Param("requirementId") Long requirementId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 统计我的对接总数（合并买卖双方，支持关键词+角色筛选+需求过滤）
     */
    long countMyConnections(@Param("tenantId") Long tenantId,
                            @Param("status") String status,
                            @Param("keyword") String keyword,
                            @Param("role") String role,
                            @Param("requirementId") Long requirementId);

    /**
     * 按ID查单条
     */
    Connection selectById(@Param("id") Long id);

    /**
     * 插入对接记录
     */
    int insert(Connection connection);

    /**
     * 按需求ID批量统计对接数（采购方视角）
     */
    List<ConnectionCountRow> countGroupByRequirementIds(@Param("buyerTenantId") Long buyerTenantId,
                                                        @Param("requirementIds") List<Long> requirementIds);

    /** 按需求统计已接受对接数（广场展示，不限买方） */
    List<ConnectionCountRow> countAcceptedByRequirementIds(@Param("requirementIds") List<Long> requirementIds);

    /**
     * 接受/拒绝/关闭对接
     */
    int updateStatus(@Param("id") Long id,
                     @Param("tenantId") Long tenantId,
                     @Param("status") String status,
                     @Param("replyMessage") String replyMessage,
                     @Param("updateBy") Long updateBy);

    /**
     * 统计某需求下已接受的对接数
     */
    long countAcceptedByRequirement(@Param("requirementId") Long requirementId);

    /**
     * 批量按需求ID查某个租户的对接记录（供广场回填我的状态）
     */
    List<Connection> batchSelectByRequirementIds(@Param("tenantId") Long tenantId,
                                                 @Param("requirementIds") List<Long> requirementIds);

    /**
     * 按需求ID+租户ID查单条对接记录
     */
    Connection selectByRequirementAndTenant(@Param("requirementId") Long requirementId,
                                            @Param("tenantId") Long tenantId);

    /**
     * 拒绝后重新申请：重置 status=pending, 更新留言, 清空拒绝原因
     */
    int updateForReapply(Connection connection);

    /**
     * 批量查当前采购商与多个供应商的最佳对接状态（accepted > pending > rejected）
     */
    List<SupplierConnectionStatusRow> selectStatusBySupplierIds(@Param("buyerTenantId") Long buyerTenantId,
                                                                @Param("supplierTenantIds") List<Long> supplierTenantIds);
}
