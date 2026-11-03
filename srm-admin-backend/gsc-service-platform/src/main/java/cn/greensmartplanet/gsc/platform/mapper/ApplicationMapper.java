package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.Application;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 入驻申请 Mapper — 分页查询 + 状态更新
 * <p>
 * 分页/筛选已改用 MP BaseMapper + LambdaQueryWrapper，不再走 XML 的 selectPage/selectCount。
 *
 * @author gsc
 */
public interface ApplicationMapper extends BaseMapper<Application> {

    /**
     * 按ID查单条（不含已删除）
     */
    Application selectById(@Param("id") Long id);

    /**
     * 按护照UUID查（防重复提交，预留）
     */
    Application selectByPassportUuid(@Param("passportUuid") String passportUuid);

    /**
     * 新增入驻申请
     */
    int insert(Application application);

    /**
     * 更新入驻申请（驳回后重新提交时用）
     */
    int update(Application application);

    /**
     * 审核操作：更新状态 + 审核意见 + 审核人 + 租户ID（通过时关联）
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewedBy") Long reviewedBy,
                     @Param("tenantId") Long tenantId);

    /**
     * 根据租户ID查询入驻申请（一个租户只有一条）
     */
    Application selectByTenantId(@Param("tenantId") Long tenantId);
}
