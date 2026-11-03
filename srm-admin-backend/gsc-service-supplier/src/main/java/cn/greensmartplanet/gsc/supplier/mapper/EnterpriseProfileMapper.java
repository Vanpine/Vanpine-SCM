package cn.greensmartplanet.gsc.supplier.mapper;

import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 企业画像 Mapper
 *
 * @author gsc
 */
public interface EnterpriseProfileMapper extends BaseMapper<EnterpriseProfile> {

    /**
     * 按租户ID查画像（不含已删除）
     */
    EnterpriseProfile selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 批量按租户ID查画像
     */
    List<EnterpriseProfile> selectByTenantIds(@Param("tenantIds") List<Long> tenantIds);

    /**
     * 插入画像
     */
    int insert(EnterpriseProfile profile);

    /**
     * 更新画像
     */
    int update(EnterpriseProfile profile);

    /**
     * 履约表现回写：仅更新 5 个履约字段（交付终态触发，避免全量覆盖画像）
     */
    int updatePerformance(@Param("tenantId") Long tenantId,
                          @Param("onTimeRate") BigDecimal onTimeRate,
                          @Param("acceptPassRate") BigDecimal acceptPassRate,
                          @Param("returnCount") Integer returnCount,
                          @Param("perfScore") BigDecimal perfScore,
                          @Param("perfSampleSize") Integer perfSampleSize);

    /**
     * 按画像完整度降序取 Top N 供应商（profile_type IN ('supplier','both')），用于智能匹配候选池截断
     */
    List<EnterpriseProfile> selectAllSuppliersWithLimit(@Param("limit") int limit);

    /**
     * 匹配召回：排除自己，按品类 token 或地域缩小候选，再按完整度截断。
     * 一次只传一个维度（品类或地域）；都空则不过滤，按完整度 TopN。
     */
    List<EnterpriseProfile> selectSuppliersForRecall(@Param("excludeTenantId") Long excludeTenantId,
                                                     @Param("categoryTokens") List<String> categoryTokens,
                                                     @Param("region") String region,
                                                     @Param("limit") int limit);

    /**
     * 统计全部供应商总数（不含 keyword 过滤，用于匹配上限告警）
     */
    long countAllSuppliers();

    /**
     * 分页查供应商（需求广场"找供应商"，支持企业名/行业/地区搜索；excludeTenantId 排除当前租户自己）
     */
    List<EnterpriseProfile> selectSuppliersPage(@Param("keyword") String keyword,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit,
                                                @Param("excludeTenantId") Long excludeTenantId);

    /**
     * 统计供应商总数
     */
    long countSuppliers(@Param("keyword") String keyword,
                        @Param("excludeTenantId") Long excludeTenantId);
}
