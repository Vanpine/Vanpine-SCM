package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.dto.CreateTenantDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateTenantDTO;
import cn.greensmartplanet.gsc.platform.entity.Tenant;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 租户管理服务接口
 * 超管用于开辟/管理入驻企业
 */
public interface TenantService {

    /**
     * 分页查询租户列表（tenantCode/companyName 各自模糊匹配，status 筛选）
     */
    IPage<Tenant> page(int pageNum, int pageSize, String tenantCode, String companyName, String status);

    /**
     * 租户详情
     */
    Tenant detail(Long id);

    /**
     * 新增租户，编码全局唯一，默认 active
     */
    void create(CreateTenantDTO dto);

    /**
     * 编辑租户基本信息（编码不可改）
     */
    void update(Long id, UpdateTenantDTO dto);

    /**
     * 审核：更新租户状态（active/suspended/rejected）
     */
    void review(Long id, String status, String comment, Long reviewerId);

    /**
     * 逻辑删除租户
     */
    void delete(Long id);
}
