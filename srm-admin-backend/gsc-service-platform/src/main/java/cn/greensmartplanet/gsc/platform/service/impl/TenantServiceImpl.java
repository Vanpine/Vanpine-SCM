package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.platform.dto.CreateTenantDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateTenantDTO;
import cn.greensmartplanet.gsc.platform.entity.Tenant;
import cn.greensmartplanet.gsc.common.enums.TenantStatus;
import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import cn.greensmartplanet.gsc.framework.security.RefreshTokenStore;
import cn.greensmartplanet.gsc.platform.entity.User;
import cn.greensmartplanet.gsc.platform.mapper.TenantMapper;
import cn.greensmartplanet.gsc.platform.mapper.UserMapper;
import cn.greensmartplanet.gsc.platform.service.TenantService;
import cn.greensmartplanet.gsc.platform.support.TenantGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 租户管理服务实现
 * 超管开辟/管理入驻企业
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final Long ROLE_TENANT_ADMIN = 2L;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;  // 用于删除停用租户下用户的 Token
    private final PermissionCache permissionCache;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 分页查询租户列表（tenantCode/companyName 各自模糊匹配，status 筛选）
     */
    @Override
    public IPage<Tenant> page(int pageNum, int pageSize, String tenantCode, String companyName, String status) {
        // 查询数据列表
        List<Tenant> records = tenantMapper.selectPage(tenantCode, companyName, status);
        // 手动分页：计算偏移和截取
        int total = records.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Tenant> pageRecords = from < total ? records.subList(from, to) : List.of();

        Page<Tenant> page = new Page<>(pageNum, pageSize);
        page.setRecords(pageRecords);
        page.setTotal(total);
        return page;
    }

    /**
     * 租户详情
     */
    @Override
    public Tenant detail(Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);  // 租户不存在
        }
        return tenant;
    }

    /**
     * 新增租户：校验编码唯一→入库→可选创建管理员账号并分配角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CreateTenantDTO dto) {
        // 1. 校验编码唯一
        Tenant exist = tenantMapper.findTenantByCode(dto.getTenantCode());
        if (exist != null) {
            throw new BizException(ErrorCode.TENANT_CODE_EXISTS);  // 租户编码已被占用
        }
        // 2. 组装实体（Builder 模式）
        Tenant tenant = Tenant.builder()
                .tenantCode(dto.getTenantCode())
                .tenantName(dto.getTenantName())
                .shortName(dto.getShortName())
                .country(dto.getCountry())
                .regNumber(dto.getRegNumber())
                .contactName(dto.getContactName())
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .status(TenantStatus.ACTIVE.getCode())
                .sortOrder(0)
                .build();
        tenantMapper.insertTenant(tenant);
        log.info("新增租户成功, tenantCode={}, tenantName={}", dto.getTenantCode(), dto.getTenantName());

        // 3. 如果填写了管理员用户名，同时创建该租户的管理员账号
        if (StringUtils.hasText(dto.getAdminUsername()) && StringUtils.hasText(dto.getAdminPassword())) {
            User admin = User.builder()
                    .tenantId(tenant.getId())
                    .username(dto.getAdminUsername())
                    .passwordHash(passwordEncoder.encode(dto.getAdminPassword()))
                    .realName(StringUtils.hasText(dto.getAdminRealName()) ? dto.getAdminRealName() : dto.getTenantName())
                    .email(dto.getContactEmail())
                    .phone(dto.getContactPhone())
                    .status(TenantStatus.ACTIVE.getCode())
                    .build();
            userMapper.insert(admin);
            userMapper.insertUserRoles(admin.getId(), List.of(ROLE_TENANT_ADMIN));
            log.info("同时创建企业管理员账号, username={}, tenantId={}", dto.getAdminUsername(), tenant.getId());
        }
    }

    /**
     * 编辑租户基本信息（编码不可改）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdateTenantDTO dto) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);  // 租户不存在
        }
        // 编码变更时校验唯一性
        if (!tenant.getTenantCode().equals(dto.getTenantCode())) {
            Tenant existing = tenantMapper.findTenantByCode(dto.getTenantCode());
            if (existing != null && !existing.getId().equals(id)) {
                throw new BizException(ErrorCode.TENANT_CODE_EXISTS);
            }
            tenant.setTenantCode(dto.getTenantCode());
        }
        tenant.setTenantName(dto.getTenantName());
        tenant.setShortName(dto.getShortName());
        tenant.setCountry(dto.getCountry());
        tenant.setRegNumber(dto.getRegNumber());
        tenant.setContactName(dto.getContactName());
        tenant.setContactEmail(dto.getContactEmail());
        tenant.setContactPhone(dto.getContactPhone());
        // MyBatis-Plus 自带 updateById
        tenantMapper.updateById(tenant);
    }

    /**
     * 审核租户：更新状态（active/suspended/rejected）+ 审核意见
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, String status, String comment, Long reviewerId) {
        // 校验租户存在
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);  // 租户不存在
        }
        // 校验状态合法性
        if (!List.of(TenantStatus.ACTIVE.getCode(), TenantStatus.SUSPENDED.getCode(), TenantStatus.REJECTED.getCode()).contains(status)) {
            throw new BizException(ErrorCode.TENANT_STATUS_INVALID);  // 无效状态值
        }
        tenantMapper.updateStatus(id, status, comment, reviewerId);  // 更新租户状态
        // 停用租户 → 踢出该租户下所有用户（带专属提示文案）
        if (TenantStatus.SUSPENDED.getCode().equals(status)) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getTenantId, id));  // 查该租户下所有用户
            users.forEach(u -> {
                TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, u.getId());
                redisTemplate.opsForValue().set(SystemConstants.KICK_REASON_PREFIX + u.getId(),
                        "所属租户已被停用，请联系管理员", Duration.ofMinutes(5));
            });
            log.info("停用租户, id={}, 已踢出 {} 个用户", id, users.size());
        }
        log.info("审核租户, id={}, status={}, reviewer={}", id, status, reviewerId);
    }

    /**
     * 逻辑删除租户（平台租户id=1不可删），删除后踢出该租户全部用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);  // 租户不存在
        }
        // 平台租户(id=1)不可删除
        if (SystemConstants.PLATFORM_TENANT_ID.equals(id)) {
            throw new BizException(ErrorCode.PLATFORM_TENANT_CANNOT_DELETE);  // 平台租户不可删除
        }
        // 先查该租户下所有用户，删除后批量踢出，防止旧 Token 继续访问
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getTenantId, id));
        tenantMapper.softDelete(id);
        users.forEach(u -> TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, u.getId()));
        log.info("删除租户, id={}, 已踢出 {} 个用户", id, users.size());
    }
}
