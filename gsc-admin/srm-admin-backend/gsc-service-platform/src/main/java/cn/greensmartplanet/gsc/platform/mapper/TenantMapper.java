package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.Tenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租户 Mapper — 简单CRUD用 BaseMapper，复杂查询写到 TenantMapper.xml
 *
 * @author gsc
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /** 根据租户编码查租户（登录用） */
    Tenant findTenantByCode(@Param("tenantCode") String tenantCode);

    /**
     * 分页查询租户列表
     * @param tenantCode  租户编码筛选（模糊匹配）
     * @param companyName 企业名称筛选（模糊匹配）
     * @param status      状态筛选，可选
     */
    List<Tenant> selectPage(@Param("tenantCode") String tenantCode,
                            @Param("companyName") String companyName,
                            @Param("status") String status);

    /** 根据ID查租户详情 */
    Tenant selectById(@Param("id") Long id);

    /**
     * 新增租户，返回自增主键
     * @return 受影响行数
     */
    int insertTenant(Tenant tenant);

    /**
     * 审核操作：更新租户状态
     * @param id             租户ID
     * @param status         新状态：active / suspended / rejected
     * @param reviewComment  审核意见
     * @param reviewedBy     审核人ID
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewedBy") Long reviewedBy);

    /** 逻辑删除租户 */
    int softDelete(@Param("id") Long id);
}
