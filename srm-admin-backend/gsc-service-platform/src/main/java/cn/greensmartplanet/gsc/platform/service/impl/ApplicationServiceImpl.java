package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;


import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.enums.ApplicationStatus;
import cn.greensmartplanet.gsc.common.enums.TenantStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.util.CurrencyAmountValidator;
import cn.greensmartplanet.gsc.common.util.DdPackageCatalog;
import cn.greensmartplanet.gsc.framework.auth.PassportClient;
import cn.greensmartplanet.gsc.framework.auth.PassportUserInfo;
import cn.greensmartplanet.gsc.framework.mail.MailService;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.platform.client.CorporatePaymentClient;
import cn.greensmartplanet.gsc.platform.client.LawFirmClient;
import cn.greensmartplanet.gsc.platform.vo.CreatePaymentVO;
import cn.greensmartplanet.gsc.platform.dto.AssignFirmDTO;
import cn.greensmartplanet.gsc.platform.dto.FirmInfo;
import cn.greensmartplanet.gsc.platform.dto.ReviewApplicationDTO;
import cn.greensmartplanet.gsc.platform.dto.SubmitApplicationDTO;
import cn.greensmartplanet.gsc.common.enums.DdFirmStatus;
import cn.greensmartplanet.gsc.platform.entity.*;
import cn.greensmartplanet.gsc.platform.entity.DueDiligenceAttachment;
import cn.greensmartplanet.gsc.platform.mapper.*;
import cn.greensmartplanet.gsc.platform.mapper.DueDiligenceAttachmentMapper;
import cn.greensmartplanet.gsc.platform.service.ApplicationService;
import cn.greensmartplanet.gsc.platform.service.NotificationService;
import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.platform.vo.ApplicationVO;
import cn.greensmartplanet.gsc.platform.vo.DueDiligenceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入驻申请服务实现 — 尽调全流程的核心 Service
 * <p>
 * 负责以下业务流程：
 * <ol>
 *   <li><b>入驻申请</b>：官网提交 → AI护照校验 → 实名检查 → 查重 → 入库</li>
 *   <li><b>审核</b>：通过 → 创建租户 + 管理员账号 + 尽调记录；驳回 → 发邮件通知</li>
 *   <li><b>尽调</b>：用户填表单 → 支付 → 分配事务所 → 事务所核查 → 提交结果</li>
 *   <li><b>核查清单</b>：按套餐动态生成核查项，事务所逐项勾选</li>
 * </ol>
 * <p>
 * 注意：尽调相关的简单 CRUD（查/改/支付/报告上传）在 DueDiligenceServiceImpl，
 * 本类负责跨表协调逻辑（审核、分配事务所、核查清单等需要同时操作多张表的场景）。
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本类含尽调寄生逻辑（读写 due_diligence/dd_checklist 表），阶段 7 gsc-service-supplier 上线后改 Feign 调用收敛。</p>
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    /**
     * 预审用户角色ID：审核通过后分配的初始角色，仅可访问尽调页面，尽调结束后由管理员升级
     * 为企业管理员
     */
    private static final Long ROLE_DUE_DILIGENCE = 5L;

    /**
     * 事务所用户角色ID：事务所账号登录后只能访问工作台
     */
    private static final Long ROLE_FIRM = 8L;

    /**
     * BUYER_ADMIN / SUPPLIER_ADMIN 平台模板角色ID
     */
    private static final Long ROLE_BUYER_ADMIN = 9L;
    private static final Long ROLE_SUPPLIER_ADMIN = 10L;

    // ---- Mapper 注入 ----
    private final ApplicationMapper applicationMapper;   // 入驻申请表
    private final TenantMapper tenantMapper;             // 租户表
    private final UserMapper userMapper;                 // 用户表
    private final DueDiligenceMapper dueDiligenceMapper; // 尽调记录表
    private final DdChecklistMapper ddChecklistMapper;   // 尽调核查清单表
    private final DueDiligenceAttachmentMapper attachmentMapper; // 尽调附件子表（多值）
    private final SanctionScreenRecordMapper sanctionScreenRecordMapper; // 制裁筛查记录

    // ---- 外部服务注入 ----
    private final PasswordEncoder passwordEncoder;       // 密码加密（创建用户时用）
    private final PassportClient passportClient;         // AI护照服务（校验用户身份）
    private final MailService mailService;               // 邮件服务（审核通知、事务所通知）
    private final OssService ossService;                 // 组装尽调 VO 时按 oss_key 现签附件 URL
    private final NotificationService notificationService; // 站内通知服务（platform 域直写落库，不走 MQ）
    private final LawFirmClient lawFirmClient;           // 外部事务所 API（搜索事务所列表）
    private final CorporatePaymentClient corporatePaymentClient;
    // 画像归供应商域，尽调确认通过后经 Feign 回调初始化（降级抛异常回滚，与事务内 upsert 语义一致）
    private final ProfileClient profileClient;
    private final RedisTemplate<String, Object> redisTemplate; // Redis（权限缓存清除）
    private final PermissionCache permissionCache;

    /**
     * 后台管理系统登录地址（配置在 application.yml 中）
     */
    @Value("${gsc.login-url}")
    private String loginUrl;

    /**
     * 前台官网地址（配置在 application.yml 中）
     */
    @Value("${gsc.website-url}")
    private String websiteUrl;

    // ==================== 查询 ====================

    /**
     * 分页查询入驻申请列表（超管后台列表页）
     * <p>
     * 采用 MP LambdaQueryWrapper 两步筛选：
     * <ol>
     *   <li>如有 DD 层过滤条件 → 查 due_diligence 获取匹配的 applicationId 列表</li>
     *   <li>查 application 表（keyword/status/applicantRole + applicationId IN）分页</li>
     *   <li>批量附加尽调数据（避免 N+1 查询）</li>
     * </ol>
     *
     * @param pageNum        页码（从1开始）
     * @param pageSize       每页条数
     * @param keyword        模糊匹配公司名/联系人/邮箱
     * @param status         审核状态筛选
     * @param applicantRole  申请身份筛选
     * @param packageType    套餐类型筛选
     * @param paymentStatus  支付状态筛选
     * @param ddFirmStatus   事务所状态筛选
     * @param ddReportStatus 报告状态筛选
     * @return {records, total, page, pageSize}
     */
    @Override
    public Map<String, Object> page(int pageNum, int pageSize, String keyword, String companyName, String contactName,
                                    String status, String applicantRole, String packageType,
                                    String paymentStatus, String ddFirmStatus, String ddReportStatus) {
        // 1. 判断是否有 DD 层过滤条件（套餐/支付/事务所状态/报告状态）
        boolean hasDdFilter = isNotBlank(packageType) || isNotBlank(paymentStatus)
                || isNotBlank(ddFirmStatus) || isNotBlank(ddReportStatus);

        // 2. 如果有 DD 过滤 → 先查 due_diligence 获取匹配的 applicationId 列表（作为子集约束）
        List<Long> ddAppIds = null;
        if (hasDdFilter) {
            LambdaQueryWrapper<DueDiligence> ddWrapper = new LambdaQueryWrapper<>();
            ddWrapper.select(DueDiligence::getApplicationId); // 只查 applicationId，省带宽
            if (isNotBlank(packageType)) ddWrapper.eq(DueDiligence::getPackageType, packageType);
            if (isNotBlank(paymentStatus)) ddWrapper.eq(DueDiligence::getPaymentStatus, paymentStatus);
            if (isNotBlank(ddFirmStatus)) {
                if (DdFirmStatus.UNASSIGNED.getCode().equals(ddFirmStatus)) {
                    ddWrapper.isNull(DueDiligence::getDdFirmStatus); // 未分配 = dd_firm_status IS NULL
                } else {
                    ddWrapper.eq(DueDiligence::getDdFirmStatus, ddFirmStatus);
                }
            }
            if (isNotBlank(ddReportStatus)) ddWrapper.eq(DueDiligence::getDdReportStatus, ddReportStatus);
            // MP 自动处理 is_deleted = 0（@TableLogic）
            List<DueDiligence> ddList = dueDiligenceMapper.selectList(ddWrapper);
            ddAppIds = ddList.stream().map(DueDiligence::getApplicationId).distinct().toList();
            // DD 过滤后没有匹配项 → 直接返回空结果
            if (ddAppIds.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("records", List.of());
                empty.put("total", 0);
                empty.put("page", pageNum);
                empty.put("pageSize", pageSize);
                return empty;
            }
        }

        // 3. 构建 Application 查询条件（MP 自动处理 is_deleted = 0）
        LambdaQueryWrapper<Application> appWrapper = new LambdaQueryWrapper<>();
        if (isNotBlank(status)) appWrapper.eq(Application::getStatus, status);
        if (isNotBlank(applicantRole)) appWrapper.eq(Application::getApplicantRole, applicantRole);
        // keyword 兜底：模糊匹配公司名/联系人/邮箱（尽调管理页面用）
        if (isNotBlank(keyword)) {
            appWrapper.and(w -> w.like(Application::getCompanyName, keyword)
                    .or().like(Application::getContactName, keyword)
                    .or().like(Application::getContactEmail, keyword));
        }
        // 精确字段筛选：公司名称 / 联系人（入驻审核页面用，每个字段独立精确匹配）
        if (isNotBlank(companyName)) appWrapper.like(Application::getCompanyName, companyName);
        if (isNotBlank(contactName)) appWrapper.like(Application::getContactName, contactName);
        // DD 过滤的子集约束
        if (ddAppIds != null && !ddAppIds.isEmpty()) {
            appWrapper.in(Application::getId, ddAppIds);
        }
        // 排序：按创建时间倒序
        appWrapper.orderByDesc(Application::getCreateTime);

        // 4. 分页查询
        Page<Application> mpPage = new Page<>(pageNum, pageSize);
        Page<Application> pageResult = applicationMapper.selectPage(mpPage, appWrapper);

        // 5. Entity → VO（Stream 流式转换）
        List<ApplicationVO> records = pageResult.getRecords().stream()
                .map(ApplicationVO::fromEntity)
                .toList();

        // 6. 批量附加尽调数据（一次查询查出所有关联的 due_diligence，避免 N+1）
        if (!records.isEmpty()) {
            List<Long> appIds = records.stream().map(ApplicationVO::getId).toList();
            List<DueDiligence> ddList = dueDiligenceMapper.selectByApplicationIds(appIds);
            // 批量查子表附件并按 applicationId 分组，避免 N+1
            Map<Long, List<DueDiligenceAttachment>> attMap = loadAttachments(
                    ddList.stream().map(DueDiligence::getApplicationId).toList());
            Map<Long, DueDiligenceVO> ddMap = new LinkedHashMap<>();
            for (DueDiligence dd : ddList) {
                ddMap.put(dd.getApplicationId(), DueDiligenceVO.fromEntity(
                        dd, attMap.getOrDefault(dd.getApplicationId(), List.of()), ossService));
            }
            for (ApplicationVO vo : records) {
                vo.setDueDiligence(ddMap.get(vo.getId()));
            }
            // 批量附加制裁筛查状态（列表页黄牌标识，避免 N+1）
            for (SanctionScreenRecord s : sanctionScreenRecordMapper.selectLatestByApplicationIds(appIds)) {
                for (ApplicationVO vo : records) {
                    if (vo.getId().equals(s.getApplicationId())) {
                        vo.setSanctionStatus(s.getStatus());
                        vo.setSanctionHitCount(s.getHitCount());
                    }
                }
            }
        }

        // 7. 组装分页结果 Map
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", pageResult.getTotal());
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 判空工具：字符串非 null 且非空白
     */
    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 入驻申请详情（含尽调数据）
     * <p>
     * 先查 application，再附加 due_diligence。
     * application 不存在时抛异常而非返回 null，因为这是管理后台操作。
     *
     * @param id 申请ID
     * @return 申请详情VO（含尽调数据）
     * @throws BizException 申请不存在时抛 APPLICATION_NOT_FOUND
     */
    @Override
    public ApplicationVO detail(Long id) {
        // 1. 查入驻申请
        Application app = applicationMapper.selectById(id);
        if (app == null) {
            throw new BizException(ErrorCode.APPLICATION_NOT_FOUND);

        }
        // 2. Entity → VO
        ApplicationVO vo = ApplicationVO.fromEntity(app);
        // 3. 附加尽调数据（审核通过后才有）
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(id);
        if (dd != null) {
            vo.setDueDiligence(toDdVO(dd));
        }
        // 4. 附加制裁筛查状态（详情页黄牌展示）
        SanctionScreenRecord screen = sanctionScreenRecordMapper.selectLatestByApplicationId(id);
        if (screen != null) {
            vo.setSanctionStatus(screen.getStatus());
            vo.setSanctionHitCount(screen.getHitCount());
        }
        return vo;
    }

    // ==================== 入驻申请提交 ====================

    /**
     * 提交入驻申请（官网公开接口，无需登录）
     * <p>
     * 完整流程：
     * <ol>
     *   <li>AI护照校验用户身份（验证 token 有效性 + 实名信息）</li>
     *   <li>实名认证检查（护照必须已完成实名认证）</li>
     *   <li>查重：同一 passport_uuid 不可重复提交待审核/已通过的申请</li>
     *   <li>组装申请实体（按买卖家角色分别填充字段）</li>
     *   <li>入库：驳回后重提交走 UPDATE，新申请走 INSERT</li>
     * </ol>
     *
     * @param dto 前端提交的入驻申请数据
     * @return {applicationId, status, createdAt}
     * @throws BizException PASSPORT_NOT_VERIFIED（未实名）、APPLICATION_DUPLICATE（重复提交）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(SubmitApplicationDTO dto) {
        // 1. AI护照校验：验证 token 有效性，获取护照用户信息（姓名、邮箱、手机等）
        PassportUserInfo passportUser = passportClient.verifyUser(dto.getPassportUuid());
        // 1a. 实名认证检查：未实名认证的用户不允许提交入驻申请
        if (!passportUser.isIdVerified()) {
            throw new BizException(ErrorCode.PASSPORT_NOT_VERIFIED);
        }

        // 2. 查重：同一护照已有非驳回记录则拒绝重复提交
        Application existing = applicationMapper.selectByPassportUuid(dto.getPassportUuid());
        if (existing != null && !ApplicationStatus.REJECTED.getCode().equals(existing.getStatus())) {
            throw new BizException(ErrorCode.APPLICATION_DUPLICATE);
        }

        // 3. 判断是否是驳回后重新提交（复用旧记录，不新建）
        boolean isResubmit = existing != null;

        // 4. 从 DTO 中提取嵌套对象，方便后续赋值
        SubmitApplicationDTO.CompanyInfo company = dto.getCompany();         // 公司基本信息
        SubmitApplicationDTO.ContactInfo contact = dto.getContact();         // 联系人信息
        SubmitApplicationDTO.BuyerProfile bp = dto.getRole().contains("buyer")       // 采购方画像（仅买方角色有）
                ? dto.getBuyerProfile() : null;
        SubmitApplicationDTO.SupplierProfile sp = dto.getRole().contains("supplier") // 供应方画像（仅卖方角色有）
                ? dto.getSupplierProfile() : null;

        if (bp != null) {
            CurrencyAmountValidator.getAmountError("年采购额", bp.getAnnualVolumeAmount(), bp.getAnnualVolumeCurrency(),
                            bp.getFiscalPeriodStart(), bp.getFiscalPeriodEnd())
                    .ifPresent(error -> {
                        throw new BizException(ErrorCode.BAD_REQUEST, error);
                    });
        }

        // 5. 组装申请实体
        //    驳回重提交：复用已有记录（保留原 id），只更新字段
        //    新申请：用 Builder 创建新对象，设置 passport_uuid
        Application app = isResubmit ? existing : Application.builder()
                .passportUuid(dto.getPassportUuid())
                .build();
        // 5a. 通用字段
        app.setApplicantRole(String.join(",", dto.getRole()));  // "buyer,supplier" 逗号分隔
        app.setStatus(ApplicationStatus.PENDING_REVIEW.getCode());  // 状态置为待审核
        app.setCompanyName(company.getName());
        app.setCountry(company.getCountry());
        app.setRegNumber(company.getRegNumber());
        app.setEstablishedYear(company.getEstablishedYear());
        app.setContactName(contact.getName());
        app.setContactTitle(contact.getTitle());
        app.setContactEmail(contact.getEmail());
        app.setContactPhone(contact.getPhone());
        // 5b. 采购方特有字段（仅当选择了买方角色时填充）
        if (bp != null) {
            app.setBuyCategory(bp.getCategory());           // 采购品类
            app.setBuyVolume(bp.getAnnualVolume());         // 年采购量
            app.setBuyVolumeAmount(bp.getAnnualVolumeAmount());
            app.setBuyVolumeCurrency(bp.getAnnualVolumeCurrency());
            app.setFiscalPeriodStart(bp.getFiscalPeriodStart());
            app.setFiscalPeriodEnd(bp.getFiscalPeriodEnd());
            app.setBuyMarket(bp.getTargetMarkets());        // 目标市场
        }
        // 5c. 供应方特有字段（仅当选择了卖方角色时填充）
        if (sp != null) {
            app.setSupCategory(sp.getCategory());           // 供应品类
            app.setSupCapacity(sp.getAnnualCapacity());     // 年产能
            app.setSupCerts(sp.getCertifications());        // 资质认证
            app.setSupExport(sp.getExportExperience());     // 出口经验
        }

        // 6. 入库
        //    驳回重提交 → UPDATE（数据库已有该记录）
        //    新申请 → INSERT
        if (isResubmit) {
            applicationMapper.update(app);
        } else {
            applicationMapper.insert(app);
        }

        // 7. 通知超管（事务提交后发送，避免"通知已落库但业务回滚"）
        final boolean resubmit = isResubmit;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPlatform("APPLICATION_SUBMIT",
                        resubmit ? "入驻申请已重新提交" : "新的入驻申请待审核",
                        "【" + app.getCompanyName() + "】"
                                + (resubmit ? "已修改资料后重新提交入驻申请" : "提交了入驻申请")
                                + "，请及时审核。",
                        app.getId());
            }
        });

        // 8. 返回提交结果（前端用 applicationId 跳转到审核等待页）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", app.getId());
        result.put("status", app.getStatus());
        result.put("createdAt", app.getCreateTime());
        return result;
    }

    // ==================== 站内通知（入驻/尽调全流程） ====================

    /**
     * 通知平台超管（接收方 = 平台租户 PLATFORM_TENANT_ID）。
     * <p>
     * 平台租户下所有用户（超管账号）共享同一收件箱——通知表只按 tenant_id 分发，无用户级投递。
     * 调用方必须置于 {@code afterCommit} 回调内，避免「通知已落库但业务回滚」的脏数据。
     * relatedType 固定 "application"，前端据此显示图标并跳转「入驻预审」页。
     *
     * @param type    事件类型（如 APPLICATION_SUBMIT）
     * @param title   通知标题
     * @param content 通知内容
     * @param appId   关联的入驻申请 ID（前端跳转定位用）
     */
    private void notifyPlatform(String type, String title, String content, Long appId) {
        try {
            notificationService.notify(Notification.builder()
                    .receiverTenantId(SystemConstants.PLATFORM_TENANT_ID) // 平台租户 = 超管收件箱
                    .type(type)
                    .title(title)
                    .content(content)
                    .relatedType("application")
                    .relatedId(appId)
                    .build());
        } catch (Exception e) {
            // 通知是旁路，失败不影响主业务（与 mailService 同策略）
            log.warn("平台站内通知发送失败: type={}, appId={}", type, appId, e);
        }
    }

    /**
     * 通知企业租户（接收方 = 申请关联的企业 tenantId）。
     * <p>
     * 与 {@link #notifyPlatform} 同构，仅接收方不同；调用方同样须置于 afterCommit 回调内。
     *
     * @param tenantId 企业租户 ID（申请审核通过后才有值）
     * @param type     事件类型（如 APPLICATION_APPROVED）
     * @param title    通知标题
     * @param content  通知内容
     * @param appId    关联的入驻申请 ID
     */
    private void notifyEnterprise(Long tenantId, String type, String title, String content, Long appId) {
        try {
            notificationService.notify(Notification.builder()
                    .receiverTenantId(tenantId)
                    .type(type)
                    .title(title)
                    .content(content)
                    .relatedType("application")
                    .relatedId(appId)
                    .build());
        } catch (Exception e) {
            log.warn("企业站内通知发送失败: type={}, tenantId={}, appId={}", type, tenantId, appId, e);
        }
    }

    // ==================== 审核 ====================

    /**
     * 审核入驻申请（核心流程，超管操作）
     * <p>
     * <b>通过流程：</b>
     * <ol>
     *   <li>校验租户编码唯一性</li>
     *   <li>创建租户（Tenant）</li>
     *   <li>创建管理员账号（User，角色=ROLE_DUE_DILIGENCE）</li>
     *   <li>发送审核通过通知邮件（异步：失败不阻塞审核）</li>
     *   <li>更新 application 状态 + 关联 tenantId</li>
     *   <li>创建尽调记录（due_diligence），拷贝 application 重叠字段作为初始值</li>
     * </ol>
     * <p>
     * <b>驳回流程：</b>
     * <ol>
     *   <li>发送驳回通知邮件（含审核意见，引导用户去官网重新提交）</li>
     *   <li>更新 application 状态 + 审核意见</li>
     * </ol>
     *
     * @param id         申请ID
     * @param dto        审核参数（status、tenantCode、adminUsername、adminPassword、reviewComment）
     * @param reviewerId 审核人ID（当前登录用户）
     * @throws BizException APPLICATION_NOT_FOUND（不存在）、APPLICATION_ALREADY_REVIEWED（已审核过）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, ReviewApplicationDTO dto, Long reviewerId) {
        // ---- 1. 校验申请存在且状态为待审核 ----
        Application app = applicationMapper.selectById(id);
        if (app == null) {
            throw new BizException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        if (!ApplicationStatus.PENDING_REVIEW.getCode().equals(app.getStatus())) {
            throw new BizException(ErrorCode.APPLICATION_ALREADY_REVIEWED); // 防止重复审核
        }

        String reviewStatus = dto.getStatus();      // "approved" 或 "rejected"
        String comment = dto.getReviewComment();     // 审核意见（驳回时必填）

        if (ApplicationStatus.APPROVED.getCode().equals(reviewStatus)) {
            // ========== 审核通过 ==========

            // 2. 校验租户编码（必填 + 唯一性）
            String tenantCode = dto.getTenantCode();
            if (tenantCode == null || tenantCode.isBlank()) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            Tenant existingTenant = tenantMapper.findTenantByCode(tenantCode);
            if (existingTenant != null) {
                throw new BizException(ErrorCode.TENANT_CODE_EXISTS); // 编码已被占用
            }

            // 3. 创建租户 → 返回 tenantId
            Long tenantId = createTenant(app, tenantCode);

            // 4. 创建管理员账号（默认用户名 admin，默认密码 admin123）
            String adminUsername = dto.getAdminUsername() != null && !dto.getAdminUsername().isBlank()
                    ? dto.getAdminUsername() : "admin";
            String adminPassword = dto.getAdminPassword() != null && !dto.getAdminPassword().isBlank()
                    ? dto.getAdminPassword() : "admin123";
            Long userId = createTenantAdmin(app, tenantId, adminUsername, adminPassword);

            // 5. 发送审核通过通知邮件（异步：邮件发送失败不影响审核结果）
            try {
                String to = app.getContactEmail();
                if (to != null && !to.isBlank()) {
                    mailService.sendApprovalNotice(to, app.getCompanyName(),
                            tenantCode, adminUsername, adminPassword, loginUrl);
                }
            } catch (Exception e) {
                log.warn("审核通知邮件发送失败, appId={}, email={}", id, app.getContactEmail(), e);
            }

            // 6. 更新 application 状态（approved + 关联 tenantId）
            applicationMapper.updateStatus(id, reviewStatus, comment, reviewerId, tenantId);

            // 7. 创建尽调记录（1:1 关联入驻申请，拷贝重叠字段作为初始值）
            //    这些字段用户在尽调表单中可以修改，不修改就用这里拷贝的初始值
            DueDiligence dd = DueDiligence.builder()
                    .applicationId(id)
                    // 基础信息
                    .companyName(app.getCompanyName())
                    .country(app.getCountry())
                    .regNumber(app.getRegNumber())
                    .establishedYear(app.getEstablishedYear())
                    // 联系信息
                    .contactName(app.getContactName())
                    .contactTitle(app.getContactTitle())
                    .contactEmail(app.getContactEmail())
                    .contactPhone(app.getContactPhone())
                    // 采购方信息
                    .buyCategory(app.getBuyCategory())
                    .buyVolume(app.getBuyVolume())
                    .buyVolumeAmount(app.getBuyVolumeAmount())
                    .buyVolumeCurrency(app.getBuyVolumeCurrency())
                    .fiscalPeriodStart(app.getFiscalPeriodStart())
                    .fiscalPeriodEnd(app.getFiscalPeriodEnd())
                    .buyMarket(app.getBuyMarket())
                    // 供应方信息
                    .supCategory(app.getSupCategory())
                    .supCapacity(app.getSupCapacity())
                    .supCerts(app.getSupCerts())
                    .supExport(app.getSupExport())
                    // 申请身份
                    .applicantRole(app.getApplicantRole())
                    // 初始状态：未支付 + 报告待生成
                    .paymentStatus("unpaid")
                    .ddReportStatus("pending")
                    .build();
            dueDiligenceMapper.insert(dd); // 插入 due_diligence 表

            // 7b. 通知企业审核通过（事务提交后发站内信，与邮件双通道并存）
            final Long approvedTenantId = tenantId;
            final String approvedAdmin = adminUsername;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyEnterprise(approvedTenantId, "APPLICATION_APPROVED", "入驻申请已通过",
                            "贵司入驻申请已审核通过，租户编码：" + tenantCode
                                    + "，管理员账号：" + approvedAdmin
                                    + "。请登录后完成尽职调查以开通完整权限。",
                            id);
                }
            });

            log.info("入驻申请审核通过, appId={}, tenantId={}, tenantCode={}, reviewerId={}", id, tenantId, tenantCode, reviewerId);

        } else if (ApplicationStatus.REJECTED.getCode().equals(reviewStatus)) {
            // ========== 审核驳回 ==========

            // 发送驳回通知邮件（含审核意见，引导用户修改后去官网重新提交）
            try {
                String to = app.getContactEmail();
                if (to != null && !to.isBlank() && comment != null && !comment.isBlank()) {
                    mailService.sendRejectionNotice(to, app.getCompanyName(), comment, websiteUrl);
                }
            } catch (Exception e) {
                log.warn("驳回通知邮件发送失败, appId={}, email={}", id, app.getContactEmail(), e);
            }

            // 更新 application 状态（rejected + 审核意见，tenantId 传 null 表示未创建租户）
            applicationMapper.updateStatus(id, reviewStatus, comment, reviewerId, null);

            // 通知企业审核驳回（事务提交后发站内信）
            // 注：驳回场景企业通常尚无租户（app.tenantId 为 null）→ 此时跳过站内信，仅保留官网引导邮件；
            //     若该企业此前已有租户（如审核通过后被撤回重审），则仍能收到站内信
            final Long rejectedTenantId = app.getTenantId();
            if (rejectedTenantId != null) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notifyEnterprise(rejectedTenantId, "APPLICATION_REJECTED", "入驻申请未通过",
                                "贵司入驻申请未通过审核。审核意见：" + (comment == null ? "无" : comment)
                                        + "。请修改资料后重新提交。",
                                id);
                    }
                });
            }

            log.info("入驻申请已驳回, appId={}, comment={}, reviewerId={}", id, comment, reviewerId);

        } else {
            // 非法的审核状态值（既不是 approved 也不是 rejected）
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    /**
     * 创建租户（审核通过时调用）
     * <p>
     * 从申请信息中提取字段，构建 Tenant 实体并入库。
     * 租户编码由审核人在审核表单中手动填写。
     *
     * @param app        入驻申请实体
     * @param tenantCode 租户编码（审核人指定，全局唯一）
     * @return 新创建的租户ID
     */
    private Long createTenant(Application app, String tenantCode) {
        // 用 Builder 构建租户实体
        Tenant tenant = Tenant.builder()
                .tenantCode(tenantCode)
                .tenantName(app.getCompanyName())           // 租户名 = 公司全称
                .shortName(extractShortName(app.getCompanyName())) // 简称 = 从公司名提取的品牌名
                .country(app.getCountry())
                .regNumber(app.getRegNumber())
                .contactName(app.getContactName())
                .contactEmail(app.getContactEmail())
                .contactPhone(app.getContactPhone())
                .status(TenantStatus.ACTIVE.getCode())                           // 新租户默认启用
                .build();
        tenantMapper.insert(tenant); // INSERT 后 MyBatis 自动回填 id
        return tenant.getId();
    }

    /**
     * 为租户创建预审用户账号（审核通过时调用）
     * <p>
     * 角色为 ROLE_DUE_DILIGENCE（5），仅可访问尽调页面。
     * 尽调通过后由管理员在后台升级为 TENANT_ADMIN（企业管理员）。
     * <p>
     * 用户信息优先级：AI护照实名信息 > 申请时填的联系人信息
     *
     * @param app      入驻申请实体
     * @param tenantId 新创建的租户ID
     * @param username 管理员用户名（审核人指定，默认 "admin"）
     * @param password 管理员密码（审核人指定，默认 "admin123"）
     * @return 新创建的用户ID
     */
    private Long createTenantAdmin(Application app, Long tenantId, String username, String password) {
        // 1. 获取护照用户信息（用于填充 passport 相关字段 + 兜底姓名/邮箱/手机）
        PassportUserInfo passportUser = passportClient.verifyUser(app.getPassportUuid());

        // 2. 构建用户实体：护照实名信息优先，兜底用申请时填的
        User admin = User.builder()
                .tenantId(tenantId)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))  // 密码 BCrypt 加密
                // 姓名/邮箱/手机：护照实名信息优先 → 申请信息兜底
                .realName(passportUser.getRealName() != null ? passportUser.getRealName() : app.getContactName())
                .email(passportUser.getEmail() != null ? passportUser.getEmail() : app.getContactEmail())
                .phone(passportUser.getPhone() != null ? passportUser.getPhone() : app.getContactPhone())
                // 护照字段：用于后续同步 AI 护照信息
                .passportUuid(passportUser.getUuid())
                .passportName(passportUser.getRealName())
                .passportEmail(passportUser.getEmail())
                .passportPhone(passportUser.getPhone())
                .status(TenantStatus.ACTIVE.getCode())  // 新用户默认启用
                .build();
        userMapper.insert(admin); // INSERT 后自动回填 id

        // 3. 分配预审用户角色（ROLE_DUE_DILIGENCE=5），前端路由守卫根据此角色限制只能访问尽调页面
        userMapper.insertUserRoles(admin.getId(), List.of(ROLE_DUE_DILIGENCE));
        log.info("预审用户账号已创建, tenantId={}, username={}, userId={}, role=DUE_DILIGENCE",
                tenantId, username, admin.getId());
        return admin.getId();
    }

    /**
     * 从公司全称提取简称：去掉地名前缀和公司后缀，保留品牌名
     * <p>
     * 例："深圳智联电子科技有限公司" → "智联电子"
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>去掉常见公司后缀（有限公司、科技、技术等）</li>
     *   <li>去掉常见城市/省份前缀</li>
     *   <li>去掉中港台等前缀（仅当结果够长时，避免误删品牌名）</li>
     * </ol>
     *
     * @param fullName 公司全称
     * @return 提取的品牌简称，提取失败返回 null
     */
    private String extractShortName(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;

        // 1. 去掉常见公司后缀（用正则依次匹配移除）
        String result = fullName
                .replaceAll("(股份)?有限公司$", "")   // "科技有限公司" → "科技"
                .replaceAll("（中国）$", "")           // 中文括号中国后缀
                .replaceAll("\\(中国\\)$", "")          // 英文括号中国后缀
                .replaceAll("科技$|技术$|实业$|集团$|控股$", "") // 常见行业后缀
                .trim();

        // 2. 去掉常见城市/省份前缀（直辖市 + 主要城市 + 省份名）
        result = result.replaceAll("^(深圳|北京|上海|广州|杭州|成都|武汉|南京|天津|重庆|苏州|东莞|珠海|厦门|"
                        + "广东|浙江|江苏|山东|福建|四川|湖北|湖南|河南|河北|安徽)", "")
                .trim();

        // 3. 去掉剩余的地理前缀（仅当结果长度>4时才去掉，避免"中国移动"→"移动"这种误删）
        if (result.length() > 4) {
            result = result.replaceAll("^(中国|香港|澳门|台湾)", "").trim();
        }

        // 4. 如果全被去掉了（极端情况），返回 null 而非空字符串
        return result.isEmpty() ? null : result;
    }

    // ==================== 支付 ====================

    /**
     * 创建尽调支付单 — 调中台下单拿收款码，不改 paid。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatePaymentVO createPayment(Long id, String packageType, String paymentCurrency) {
        // 1. 校验申请存在且已通过审核
        Application app = applicationMapper.selectById(id);
        if (app == null || !"approved".equals(app.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 2. 查尽调记录（审核通过时一定会创建，这里再判一次防止极端情况）
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(id);
        if (dd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "尽调记录不存在");
        }

        // 3. 防止重复支付：已支付的不允许再次支付
        if ("paid".equals(dd.getPaymentStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该申请已完成支付，无需重复支付");
        }

        // 4. 金额以后端价目表为准，拒绝客户端自报金额 / 未开通币种
        if (!DdPackageCatalog.isSupportedPackage(packageType)) {
            throw new BizException(ErrorCode.DD_PACKAGE_NOT_FOUND);
        }
        if (!DdPackageCatalog.isSupportedCurrency(paymentCurrency)) {
            throw new BizException(ErrorCode.DD_PAYMENT_CURRENCY_UNSUPPORTED);
        }
        if (!"CNY".equals(paymentCurrency)) {
            throw new BizException(ErrorCode.PAYMENT_CHANNEL_CNY_ONLY);
        }
        Long paymentAmount = DdPackageCatalog.findPrice(packageType, paymentCurrency)
                .orElseThrow(() -> new BizException(ErrorCode.DD_PACKAGE_NOT_FOUND));

        validatePackageFields(packageType, app.getApplicantRole(), dd);

        java.math.BigDecimal amountYuan = java.math.BigDecimal.valueOf(paymentAmount, 2);
        String title = "尽调套餐-" + packageType + "-" + paymentCurrency;
        com.fasterxml.jackson.databind.JsonNode created = corporatePaymentClient.createOrder(
                amountYuan, title, id, "applicationId=" + id);
        String outTradeNo = CorporatePaymentClient.text(created, "out_trade_no");
        String orderNo = CorporatePaymentClient.text(created, "order_no");
        if (orderNo == null) {
            orderNo = CorporatePaymentClient.text(created, "order_id");
        }
        String qrcode = CorporatePaymentClient.text(created, "qrcode");

        int rows = dueDiligenceMapper.updatePaymentIntent(id, packageType, paymentAmount, paymentCurrency,
                outTradeNo, orderNo);
        if (rows == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "支付单创建失败，状态可能已变更");
        }
        log.info("支付单已创建 appId={} outTradeNo={} orderNo={}", id, outTradeNo, orderNo);
        return CreatePaymentVO.builder()
                .qrcode(qrcode)
                .outTradeNo(outTradeNo)
                .orderNo(orderNo)
                .amountYuan(amountYuan.toPlainString())
                .currency(paymentCurrency)
                .build();
    }

    /**
     * 回调入账：对单 → 判成功 → 改 paid → 提交后再通知运营并回写中台。
     * 前端跳转只看本方法写出的 {@code payment_status}，不看中台 /paid 是否成功。
     *
     * @param body 已拆开 biz_content 的字段；缺 out_trade_no 直接 7007
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentNotify(com.fasterxml.jackson.databind.JsonNode body) {
        String outTradeNo = firstNotifyText(body, "out_trade_no", "outTradeNo");
        if (outTradeNo == null || outTradeNo.isBlank()) {
            throw new BizException(ErrorCode.PAYMENT_NOTIFY_INVALID, "回调缺少商户订单号 out_trade_no");
        }
        DueDiligence dd;
        try {
            dd = dueDiligenceMapper.selectOne(new LambdaQueryWrapper<DueDiligence>()
                    .eq(DueDiligence::getPaymentOutTradeNo, outTradeNo)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.error("按商户订单号查支付单失败 outTradeNo={}", outTradeNo, e);
            throw new BizException(ErrorCode.PAYMENT_NOTIFY_INVALID, "支付单查询失败：" + outTradeNo);
        }
        if (dd == null) {
            throw new BizException(ErrorCode.PAYMENT_NOTIFY_INVALID, "支付单不存在：" + outTradeNo);
        }
        // 工行会重试，已入账直接成功，避免重复改状态
        if ("paid".equals(dd.getPaymentStatus())) {
            log.info("支付回调重复，已是 paid outTradeNo={}", outTradeNo);
            return;
        }
        if (isFailedNotify(body)) {
            throw new BizException(ErrorCode.PAYMENT_NOTIFY_INVALID, "回调未支付成功，不入账");
        }
        // 银行 order_id（201337…）和下单返回的 order_no（P2026…）本来就不是同一个号
        String orderNo = firstNotifyText(body, "order_id", "order_no", "orderNo", "channel_order_id");
        if (dd.getPaymentOrderNo() != null && orderNo != null && !dd.getPaymentOrderNo().equals(orderNo)) {
            log.warn("回调银行订单号与下单记录不同 stored={} notify={} outTradeNo={}",
                    dd.getPaymentOrderNo(), orderNo, outTradeNo);
        }
        int payRows = dueDiligenceMapper.updatePaymentStatus(dd.getApplicationId(), dd.getPackageType(),
                dd.getPaymentAmount(), dd.getPaymentCurrency());
        if (payRows == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "支付状态更新失败，可能已变更");
        }
        Application app = applicationMapper.selectById(dd.getApplicationId());
        final String pkg = dd.getPackageType();
        final Long appId = dd.getApplicationId();
        final String company = app != null ? app.getCompanyName() : String.valueOf(appId);
        // /paid 要的是银行订单号；没有银行号时才退回下单时存的中台单号
        final String bankOrderId = orderNo != null ? orderNo : dd.getPaymentOrderNo();
        final com.fasterxml.jackson.databind.JsonNode notifyBody = body;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPlatform("APPLICATION_PAY", "企业已完成尽调支付",
                        "【" + company + "】已完成 " + pkg + " 套餐支付，请及时分配事务所开展尽调。",
                        appId);
                corporatePaymentClient.markPaid(outTradeNo, bankOrderId, notifyBody);
            }
        });
        log.info("支付回调已入账 appId={} outTradeNo={}", appId, outTradeNo);
    }

    /**
     * 从根节点或 data 节点取第一个非空文本。
     * 兼容中台 JSON 和拆开后的工行字段。
     */
    private static String firstNotifyText(com.fasterxml.jackson.databind.JsonNode body, String... fields) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode data = body.path("data");
        for (String field : fields) {
            String value = textOrNull(body.path(field));
            if (value != null) {
                return value;
            }
            if (!data.isMissingNode()) {
                value = textOrNull(data.path(field));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    @Override
    public String getPaymentStatus(Long applicationId) {
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        return dd == null ? null : dd.getPaymentStatus();
    }

    /**
     * 判断这次通知是不是失败单，失败则不能入账。
     * 工行约定 {@code return_code=0} 是成功，不要把 0 当成未支付。
     * 没有结果字段时，只要对上了商户单号，就按成功通知处理。
     *
     * @param body 已拆开的回调字段
     * @return true 表示明确失败，调用方应拒绝改 paid
     */
    private static boolean isFailedNotify(com.fasterxml.jackson.databind.JsonNode body) {
        String returnCode = firstNotifyText(body, "return_code", "returnCode");
        if (returnCode != null && !"0".equals(returnCode.trim())) {
            return true;
        }
        String returnMsg = firstNotifyText(body, "return_msg", "returnMsg");
        if (returnMsg != null) {
            String normalized = returnMsg.trim().toLowerCase();
            if ("fail".equals(normalized) || "failed".equals(normalized) || "失败".equals(returnMsg.trim())) {
                return true;
            }
        }
        String payStatus = firstNotifyText(body, "pay_status", "payStatus", "pay_status_text");
        if (payStatus == null) {
            return false;
        }
        String normalized = payStatus.trim().toLowerCase();
        return "unpaid".equals(normalized) || "待支付".equals(payStatus.trim());
    }

    /**
     * 校验套餐必填字段（支付前调用）
     * <p>
     * 基础版（basic）：无需额外校验
     * 标准版（standard）：需员工人数 + 年营业额 + 股权结构文件
     * 高级版（premium）：需标准版全部 + 审计报告文件
     *
     * @param packageType 套餐类型
     * @param role        申请身份（buyer/supplier/buyer,supplier）
     * @param d           尽调实体
     */
    private void validatePackageFields(String packageType, String role, DueDiligence d) {
        // 默认基础版
        if (packageType == null) packageType = "basic";

        // 标准版和高级版通用校验
        if ("standard".equals(packageType) || "premium".equals(packageType)) {
            requireNotBlank(d.getEmployeeCount(), "请选择员工人数");
            if (d.getAnnualRevenueAmount() != null || d.getAnnualRevenueCurrency() != null) {
                CurrencyAmountValidator.getAmountError("年营收", d.getAnnualRevenueAmount(), d.getAnnualRevenueCurrency(),
                                d.getFiscalPeriodStart(), d.getFiscalPeriodEnd())
                        .ifPresent(error -> {
                            throw new BizException(ErrorCode.BAD_REQUEST, error);
                        });
            } else {
                requireNotBlank(d.getAnnualRevenue(), "请填写年营收金额和币种");
            }
            requireNotBlank(d.getShareholderInfoKey(), "请上传股权结构文件");
        }

        // 高级版额外校验
        if ("premium".equals(packageType)) {
            requireNotBlank(d.getFinancialReportKey(), "请上传审计报告");
        }
    }

    // ==================== 尽调信息管理 ====================

    /**
     * 保存尽调扩展信息（用户在尽调页面自行填写）
     * <p>
     * 前置校验：
     * <ol>
     *   <li>申请必须存在</li>
     *   <li>尽调通用必填字段（省份、城市、营业执照、法人信息）不能为空</li>
     * </ol>
     * <p>
     * 有则更新，无则新增（极端情况：审核通过后尽调记录被异常删除）。
     *
     * @param id 申请ID
     * @param dd 前端提交的尽调实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDdInfo(Long id, DueDiligence dd) {
        // 1. 校验申请存在
        Application app = applicationMapper.selectById(id);
        if (app == null) {
            throw new BizException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        validateDueDiligenceAmounts(dd);

        // 2. 设置关联关系
        dd.setApplicationId(id);

        // 3. 校验尽调通用必填字段（所有套餐都需要）
        validateDdFields(app.getApplicantRole(), dd);

        // 4. 有则更新，无则新增
        DueDiligence exist = dueDiligenceMapper.selectByApplicationId(id);
        if (exist != null) {
            dd.setId(exist.getId());    // 保留原 id，走 UPDATE
            // 前端不传的字段，保留原值防止 UPDATE 时被覆盖为 NULL
            // 申请身份
            if (dd.getApplicantRole() == null) dd.setApplicantRole(exist.getApplicantRole());
            // 金额 / 币种 / 期间（旧客户端漏传时保留原值，防止被 UPDATE 覆盖为 NULL）
            if (dd.getBuyVolumeAmount() == null) dd.setBuyVolumeAmount(exist.getBuyVolumeAmount());
            if (dd.getBuyVolumeCurrency() == null) dd.setBuyVolumeCurrency(exist.getBuyVolumeCurrency());
            if (dd.getFiscalPeriodStart() == null) dd.setFiscalPeriodStart(exist.getFiscalPeriodStart());
            if (dd.getFiscalPeriodEnd() == null) dd.setFiscalPeriodEnd(exist.getFiscalPeriodEnd());
            if (dd.getAnnualRevenueAmount() == null) dd.setAnnualRevenueAmount(exist.getAnnualRevenueAmount());
            if (dd.getAnnualRevenueCurrency() == null) dd.setAnnualRevenueCurrency(exist.getAnnualRevenueCurrency());
            // 套餐/支付
            dd.setPackageType(exist.getPackageType());
            dd.setPaymentStatus(exist.getPaymentStatus());
            dd.setPaymentAmount(exist.getPaymentAmount());
            dd.setDdSubmittedAt(exist.getDdSubmittedAt());
            // 事务所工作流字段
            dd.setDdFirmId(exist.getDdFirmId());
            dd.setDdFirmName(exist.getDdFirmName());
            dd.setDdFirmStatus(exist.getDdFirmStatus());
            dd.setDdFirmProgress(exist.getDdFirmProgress());
            dd.setDdFirmAssignedAt(exist.getDdFirmAssignedAt());
            dd.setDdFirmDeadline(exist.getDdFirmDeadline());
            dd.setDdFirmInstruction(exist.getDdFirmInstruction());
            dd.setDdFirmReturnReason(exist.getDdFirmReturnReason());
            // 报告
            dd.setDdReportKey(exist.getDdReportKey());
            dd.setDdReportName(exist.getDdReportName());
            dd.setDdReportStatus(exist.getDdReportStatus());
            dd.setDdReportTime(exist.getDdReportTime());
            // 重新提交时间默认沿用原值（防 NULL 覆盖）；退回恢复时在下方覆盖为当前时间
            dd.setDdResubmittedAt(exist.getDdResubmittedAt());
            // 如果当前是事务所退回状态，企业补充材料后自动恢复为 assigned，清空退回原因
            final boolean isRecovering = DdFirmStatus.RETURNED.getCode().equals(exist.getDdFirmStatus());
            if (isRecovering) {
                dd.setDdFirmStatus(DdFirmStatus.ASSIGNED.getCode());
                dd.setDdFirmReturnReason(null);
                // 记录本次重新提交时间（单字段，多次重提覆盖为最新）
                dd.setDdResubmittedAt(LocalDateTime.now());
                log.info("尽调退回后企业已补充材料，状态自动恢复, appId={}", id);
            }
            dueDiligenceMapper.update(dd);

            // 退回恢复后通知事务所：事务提交后发邮件，企业已重新提交，事务所需重新核查
            if (isRecovering) {
                final Long firmId = exist.getDdFirmId();
                final String firmName = exist.getDdFirmName();
                final String companyName = exist.getCompanyName();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            // 按 firmId 查找事务所账号（平台级别用户，tenantId=1）
                            List<User> firmUsers = userMapper.selectList(
                                    new LambdaQueryWrapper<User>().eq(User::getFirmId, firmId));
                            if (!firmUsers.isEmpty()) {
                                String to = firmUsers.get(0).getEmail();
                                if (to != null && !to.isBlank()) {
                                    mailService.sendDueDiligenceResubmitted(to, companyName, firmName, loginUrl);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("企业重新提交通知邮件发送失败, appId={}", id, e);
                        }
                    }
                });
            }
        } else {
            dueDiligenceMapper.insert(dd); // 无记录走 INSERT（极端情况）
        }
        log.info("尽调扩展信息已保存, appId={}", id);
    }

    /**
     * 校验尽调通用必填字段（所有套餐、所有角色都需要）
     * <p>
     * 这些是尽调表单的基础校验项，无论选择什么套餐都必须填写。
     *
     * @param role 申请身份（当前仅用作日志上下文，校验规则不区分买卖家）
     * @param d    尽调实体
     */
    private void validateDdFields(String role, DueDiligence d) {
        requireNotBlank(d.getProvince(), "省份不能为空");               // 企业所在省份
        requireNotBlank(d.getCity(), "城市不能为空");                   // 企业所在城市
        requireNotBlank(d.getBusinessLicenseKey(), "请上传营业执照");    // 营业执照扫描件
        requireNotBlank(d.getLegalPersonName(), "请输入法人姓名");      // 法定代表人姓名
        requireNotBlank(d.getLegalPersonId(), "请输入法人身份证号");    // 法定代表人身份证号
    }

    private void validateDueDiligenceAmounts(DueDiligence d) {
        CurrencyAmountValidator.getAmountError("年采购额", d.getBuyVolumeAmount(), d.getBuyVolumeCurrency(),
                        d.getFiscalPeriodStart(), d.getFiscalPeriodEnd())
                .ifPresent(error -> {
                    throw new BizException(ErrorCode.BAD_REQUEST, error);
                });
        CurrencyAmountValidator.getAmountError("年营收", d.getAnnualRevenueAmount(), d.getAnnualRevenueCurrency(),
                        d.getFiscalPeriodStart(), d.getFiscalPeriodEnd())
                .ifPresent(error -> {
                    throw new BizException(ErrorCode.BAD_REQUEST, error);
                });
    }

    /**
     * 通用非空校验辅助方法
     * <p>
     * 值为 null 或 trim 后为空字符串时抛 BizException。
     *
     * @param value   待校验字符串
     * @param message 校验失败时的提示信息
     * @throws BizException 值为空时抛出，code=BAD_REQUEST
     */
    private void requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 根据租户ID查询入驻申请（尽调页面用）
     * <p>
     * 登录用户通过租户ID查到自己企业的申请信息 + 尽调数据。
     *
     * @param tenantId 租户ID（从 JWT 中获取）
     * @return 申请详情VO（含尽调数据）
     * @throws BizException 申请不存在时抛 APPLICATION_NOT_FOUND
     */
    @Override
    public ApplicationVO getByTenantId(Long tenantId) {
        // 1. 按 tenantId 查申请（审核通过时已关联）
        Application app = applicationMapper.selectByTenantId(tenantId);
        if (app == null) {
            throw new BizException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 2. Entity → VO
        ApplicationVO vo = ApplicationVO.fromEntity(app);

        // 3. 附加尽调数据
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(app.getId());
        if (dd != null) {
            vo.setDueDiligence(toDdVO(dd));
        }
        return vo;
    }

    // ==================== 事务所管理 ====================

    /**
     * 分配事务所（超管操作）
     * <p>
     * 完整流程：
     * <ol>
     *   <li>校验申请存在</li>
     *   <li>更新尽调记录：记录事务所ID、名称、状态=assigned、截止日期、特别指示</li>
     *   <li>初始化核查清单：先清旧数据，再根据套餐类型动态生成核查项</li>
     *   <li>创建或查找事务所用户账号（平台级别，tenantId=1），默认密码 admin123</li>
     *   <li>新账号发送邮件通知事务所</li>
     * </ol>
     *
     * @param applicationId 申请ID
     * @param dto           分配参数（firmId, firmName, firmContactEmail, firmContactName, firmContactPhone, deadline, instruction）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignFirm(Long applicationId, AssignFirmDTO dto) {
        // 1. 校验申请存在
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BizException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 2. 校验尽调记录存在且已支付（只有支付完成后才能分配事务所）
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "尽调记录不存在");
        }
        if (!"paid".equals(dd.getPaymentStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先完成套餐支付后再分配事务所");
        }

        // 3. 更新尽调记录——标记事务所信息 + 状态=assigned（已分配）
        int rows = dueDiligenceMapper.updateFirmInfo(applicationId, dto.getFirmId(), dto.getFirmName(),
                "assigned", dto.getDeadline(), dto.getInstruction());
        if (rows == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分配失败，状态可能已变更");
        }

        // 4. 初始化核查清单（根据套餐类型动态生成）
        {
            // 4a. 先清旧清单（重新分配事务所时需要刷新核查项）
            ddChecklistMapper.deleteByDdId(dd.getId());
            // 4b. 根据套餐类型 + 申请身份动态生成核查项列表
            List<String[]> checklistItems = buildChecklistItems(dd.getPackageType(), dd.getApplicantRole());
            // 4c. 组装 DdChecklist 实体列表
            List<DdChecklist> items = new ArrayList<>();
            for (String[] item : checklistItems) {
                DdChecklist c = new DdChecklist();
                c.setDdId(dd.getId());       // 关联尽调记录ID
                c.setItemCode(item[0]);      // 核查项编码（如 "biz_info"）
                c.setItemName(item[1]);      // 核查项显示名称（如 "工商注册信息已核实"）
                c.setGroupName(item[2]);     // 核查项分组名（对齐诺臣8大类，如"一、公司基本情况"）
                items.add(c);
            }
            // 4d. 批量插入核查清单项
            ddChecklistMapper.insertBatch(items);
        }

        // 5. 创建或查找事务所用户账号（平台级别，不属于任何企业租户）
        User firmUser = userMapper.selectByEmail(dto.getFirmContactEmail());
        if (firmUser == null) {
            // 5a. 新事务所：创建平台级用户 + 分配 FIRM 角色 + 发邮件通知
            String email = dto.getFirmContactEmail();
            if (email == null || !email.contains("@")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "事务所联系邮箱格式不正确");
            }
            String username = email.split("@")[0]; // 邮箱前缀作为用户名
            String password = "admin123";                                // 默认密码
            firmUser = User.builder()
                    .tenantId(SystemConstants.PLATFORM_TENANT_ID)  // 事务所是平台级别（tenantId=1）
                    .username(username)
                    .passwordHash(passwordEncoder.encode(password))
                    .realName(dto.getFirmContactName())            // 事务所联系人
                    .email(dto.getFirmContactEmail())
                    .phone(dto.getFirmContactPhone())
                    .firmId(dto.getFirmId())                      // 关联外部事务所ID
                    .status(TenantStatus.ACTIVE.getCode())
                    .build();
            userMapper.insert(firmUser);
            // 分配 FIRM 角色（8），前端路由守卫根据此角色限制只能访问工作台
            userMapper.insertUserRoles(firmUser.getId(), List.of(ROLE_FIRM));
            log.info("事务所账号已创建, firmId={}, username={}, email={}", dto.getFirmId(), username, dto.getFirmContactEmail());

            // 5b. 发送邮件通知事务所（异步：失败不影响分配流程）
            try {
                mailService.sendFirmCredentials(dto.getFirmContactEmail(), dto.getFirmName(), username, password, loginUrl);
            } catch (Exception e) {
                log.warn("事务所账号邮件发送失败, firmId={}, email={}", dto.getFirmId(), dto.getFirmContactEmail(), e);
            }
        } else if (firmUser.getFirmId() == null) {
            // 5c. 已有用户但未关联事务所（可能是之前创建的平台用户），补设 firmId
            userMapper.updateFirmId(firmUser.getId(), dto.getFirmId());
        }
        // 如果 firmUser 已有且已关联 firmId，说明是重复分配同一事务所，跳过账号创建

        log.info("事务所已分配, appId={}, firmId={}, firmName={}", applicationId, dto.getFirmId(), dto.getFirmName());
    }

    /**
     * 事务所工作台数据（FIRM 角色登录后看到的已分配企业列表）
     * <p>
     * 查当前事务所的所有尽调记录 + 每条记录的核查清单。
     *
     * @param firmId 事务所ID（从 JWT 中获取）
     * @return 尽调VO列表（含核查清单）
     */
    @Override
    public List<DueDiligenceVO> getFirmWorkbench(Long firmId) {
        // 校验：非事务所角色拒绝访问（Controller 传 null 时在此拦截）
        if (firmId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前用户非事务所账号");
        }
        // 1. 按事务所ID查所有已分配的尽调记录
        List<DueDiligence> list = dueDiligenceMapper.selectByFirmId(firmId);
        if (list.isEmpty()) return List.of();

        // 2. 批量查所有核查清单（一次查询替代 N 次，避免 N+1）
        List<Long> ddIds = list.stream().map(DueDiligence::getId).toList();
        List<DdChecklist> allChecklist = ddChecklistMapper.selectByDdIds(ddIds);
        // 构建 ddId → 核查清单列表 的映射
        Map<Long, List<DdChecklist>> checklistMap = new LinkedHashMap<>();
        for (DdChecklist item : allChecklist) {
            checklistMap.computeIfAbsent(item.getDdId(), k -> new ArrayList<>()).add(item);
        }

        // 3. applicantRole 兜底自愈：dd 表为 null 时从 application 取并回写（历史数据兼容，惰性查询）
        //    在 stream 之前独立循环处理，避免在函数式操作中混入数据库写副作用
        for (DueDiligence dd : list) {
            if (dd.getApplicantRole() == null) {
                Application app = applicationMapper.selectById(dd.getApplicationId());
                if (app != null && app.getApplicantRole() != null) {
                    dd.setApplicantRole(app.getApplicantRole());
                    dueDiligenceMapper.update(dd); // 回写补全，下次无需兜底
                }
            }
        }

        // 4. Entity → VO + 附加核查清单
        Map<Long, List<DueDiligenceAttachment>> attMap = loadAttachments(
                list.stream().map(DueDiligence::getApplicationId).toList());
        return list.stream().map(dd -> {
            DueDiligenceVO vo = DueDiligenceVO.fromEntity(
                    dd, attMap.getOrDefault(dd.getApplicationId(), List.of()), ossService);
            vo.setChecklist(checklistMap.getOrDefault(dd.getId(), List.of()));
            return vo;
        }).toList();
    }

    /**
     * 事务所提交尽调结果
     * <p>
     * 提交后状态变为 submitted（待管理员确认），不再自动完成。
     * 管理员确认通过后才升级角色 + 发邮件通知企业。
     * <p>
     * 处理过程：
     * <ol>
     *   <li>校验：尽调记录存在 + 确实分配给当前事务所</li>
     *   <li>状态前置校验：只有 assigned 才能提交</li>
     *   <li>解析报告文件参数（格式：url|originalName，用竖线拼接）</li>
     *   <li>更新尽调记录：状态=submitted + 报告信息</li>
     * </ol>
     *
     * @param applicationId 申请ID
     * @param firmId        事务所ID（从JWT获取，用于权限校验）
     * @param progress      事务所工作备注/说明
     * @param resultUrl     报告文件参数（格式：url|originalName，竖线分隔）
     * @throws BizException 尽调记录不存在或不属于当前事务所
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitFirmResult(Long applicationId, Long firmId, String progress, String resultUrl) {
        // 校验：非事务所角色拒绝访问
        if (firmId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前用户非事务所账号");
        }
        // 1. 校验：尽调记录存在且确实分配给当前事务所
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null || dd.getDdFirmId() == null || !dd.getDdFirmId().equals(firmId)) {
            throw new BizException(ErrorCode.BAD_REQUEST); // 防止事务所A提交事务所B的尽调
        }

        // 2. 状态前置校验：只有 assigned 或 in_progress 可以提交
        if (!DdFirmStatus.ASSIGNED.getCode().equals(dd.getDdFirmStatus()) && !DdFirmStatus.IN_PROGRESS.getCode().equals(dd.getDdFirmStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, dd.getDdFirmStatus() == null
                    ? "该尽调尚未分配给事务所"
                    : DdFirmStatus.SUBMITTED.getCode().equals(dd.getDdFirmStatus()) ? "该尽调已提交，等待管理员确认中"
                    : "该尽调结果已提交，无需重复操作");
        }

        // 3. 解析报告文件参数（前端拼接格式：OSS URL|原始文件名）
        String reportUrl = resultUrl;    // 报告文件 OSS 地址
        String reportName = null;        // 报告文件原始名称
        int pipeIdx = resultUrl.lastIndexOf('|'); // 找最后一个竖线位置（文件名可能含特殊字符）
        if (pipeIdx > -1) {
            reportUrl = resultUrl.substring(0, pipeIdx);       // 竖线前 = URL
            reportName = resultUrl.substring(pipeIdx + 1);     // 竖线后 = 原始文件名
        }

        // 4. 更新尽调记录：状态=submitted（待管理员确认）+ 报告信息
        //    角色升级和邮件通知移到管理员确认通过时执行
        if (resultUrl == null || resultUrl.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "报告文件参数不能为空");
        }
        int submitRows = dueDiligenceMapper.submitFirmResult(applicationId, "submitted", progress, reportUrl, reportName);
        if (submitRows == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "提交失败，状态可能已变更");
        }
        log.info("事务所已提交尽调结果（待管理员确认）, applicationId={}, firmId={}, reportName={}", applicationId, firmId, reportName);

        // 5. 通知超管尽调报告待确认（事务提交后发送）
        final String firm = dd.getDdFirmName();
        final String company = dd.getCompanyName();
        final String rptName = reportName;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyPlatform("APPLICATION_DD_SUBMITTED", "尽调报告待确认",
                        "【" + (firm == null ? "事务所" : firm) + "】已提交【" + company + "】的尽调报告"
                                + (rptName == null ? "" : "（" + rptName + "）") + "，请确认。",
                        applicationId);
            }
        });
    }

    /**
     * 管理员确认尽调通过
     * <p>
     * submitted → completed：更新状态 + 升级用户角色 + 发邮件通知企业。
     * 角色升级（DUE_DILIGENCE→TENANT_ADMIN）和邮件通知在此执行，不在事务所提交时执行。
     * <p>
     * 校验：
     * <ol>
     *   <li>尽调记录存在</li>
     *   <li>状态必须为 submitted（只有事务所提交后管理员才能确认）</li>
     * </ol>
     *
     * @param applicationId 申请ID
     * @param reviewerId    审核人ID（当前登录超管）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDdComplete(Long applicationId, Long reviewerId) {
        // 1. 校验：尽调记录存在且状态为 submitted
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "尽调记录不存在");
        }
        if (!DdFirmStatus.SUBMITTED.getCode().equals(dd.getDdFirmStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    dd.getDdFirmStatus() == null ? "该尽调尚未提交" : "该尽调状态不允许此操作");
        }

        // 2. 更新状态：submitted → completed + 报告状态 completed + 报告时间
        int rows = dueDiligenceMapper.confirmDdComplete(applicationId);
        if (rows == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "确认操作失败，状态可能已变更");
        }

        // 3. 升级用户角色：按入驻身份分配对应管理员角色
        Application app = applicationMapper.selectById(applicationId);
        if (app != null && app.getTenantId() != null) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getTenantId, app.getTenantId()));
            if (!users.isEmpty()) {
                User tenantUser = users.get(0);
                // 3a. 按 profileType 决定角色分配策略
                String profileType = resolveProfileType(app.getApplicantRole());
                Long newRoleId;
                if ("buyer".equals(profileType)) {
                    newRoleId = ROLE_BUYER_ADMIN;      // 9 — 买家企业管理员（平台模板）
                } else if ("supplier".equals(profileType)) {
                    newRoleId = ROLE_SUPPLIER_ADMIN;   // 10 — 供应商企业管理员（平台模板）
                } else {
                    newRoleId = 2L;                    // both → TENANT_ADMIN（平台模板）
                }
                // 3b. 删除旧角色（预审 DUE_DILIGENCE），分配新角色
                userMapper.deleteUserRoles(tenantUser.getId());
                userMapper.insertUserRoles(tenantUser.getId(), List.of(newRoleId));
                // 3c. 权限缓存失效（删缓存 + 递增版本号，带 TTL）
                permissionCache.invalidate(tenantUser.getId());
                log.info("管理员确认尽调通过，用户角色已升级, userId={}, tenantId={}, newRole={}, profileType={}",
                        tenantUser.getId(), app.getTenantId(), newRoleId, profileType);
            }
        }

        // 3d. 初始化企业画像（画像归供应商域，Feign 回调；降级抛异常随事务回滚，与原 upsert 语义一致）
        profileClient.initFromApplication(applicationId);

        // 4. 通知企业尽调完成（事务提交后发邮件）
        final String companyName = dd.getCompanyName();
        if (app != null && app.getTenantId() != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    List<User> users = userMapper.selectList(
                            new LambdaQueryWrapper<User>().eq(User::getTenantId, app.getTenantId()));
                    if (!users.isEmpty()) {
                        try {
                            String to = users.get(0).getEmail();
                            if (to != null && !to.isBlank()) {
                                mailService.sendDueDiligenceCompleted(to, companyName, loginUrl);
                            }
                        } catch (Exception e) {
                            log.warn("尽调完成通知邮件发送失败, applicationId={}", applicationId, e);
                        }
                    }
                }
            });
        }

        log.info("管理员已确认尽调通过, applicationId={}, reviewerId={}", applicationId, reviewerId);
    }

    /**
     * 将 applicantRole 归一化为 profileType：buyer,supplier → both
     */
    private String resolveProfileType(String applicantRole) {
        if (applicantRole == null) return "supplier";
        if (applicantRole.contains(",")) return "both";
        return applicantRole;
    }

    /**
     * 事务所退回尽调给企业补充材料
     * <p>
     * 事务所发现问题（材料不全、信息有误等）时退回给企业，企业补充后状态自动回到 assigned。
     * <p>
     * 校验：
     * <ol>
     *   <li>非事务所角色拒绝</li>
     *   <li>尽调记录存在 + 归属当前事务所</li>
     *   <li>状态必须为 assigned（已提交完成的不允许退回）</li>
     *   <li>退回原因必填</li>
     * </ol>
     *
     * @param applicationId 申请ID
     * @param firmId        事务所ID（从JWT获取，权限校验）
     * @param returnReason  退回原因（告知企业需要补充什么）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnToEnterprise(Long applicationId, Long firmId, String returnReason) {
        // 校验：非事务所角色拒绝访问
        if (firmId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前用户非事务所账号");
        }
        // 1. 退回原因必填
        if (returnReason == null || returnReason.trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请填写退回原因");
        }
        // 2. 校验：尽调记录存在且归属当前事务所
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null || dd.getDdFirmId() == null || !dd.getDdFirmId().equals(firmId)) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        // 3. 状态校验：只有 assigned 或 in_progress 才能退回
        if (!DdFirmStatus.ASSIGNED.getCode().equals(dd.getDdFirmStatus()) && !DdFirmStatus.IN_PROGRESS.getCode().equals(dd.getDdFirmStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    dd.getDdFirmStatus() == null ? "该尽调尚未分配给事务所"
                            : DdFirmStatus.COMPLETED.getCode().equals(dd.getDdFirmStatus()) ? "该尽调已完成，无法退回"
                            : "该尽调已退回，无需重复操作");
        }
        // 4. 更新状态 + 退回原因（SQL层也有归属 + 状态防御）
        int rows = dueDiligenceMapper.returnToEnterprise(applicationId, returnReason, firmId);
        if (rows == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "退回操作失败，状态可能已变更");
        }
        log.info("事务所已退回尽调, applicationId={}, firmId={}, reason={}", applicationId, firmId, returnReason);

        // 5. 通知企业用户补充材料
        //    注册 afterCommit 回调：等事务确认提交后再发邮件。
        //    如果事务回滚（网络波动/SQL失败），回调不触发，邮件不发，DB也没变。
        //    如果邮件发送失败，仅 log 不抛异常，DB 已正确更新，用户登录后能在页面看到退回状态。
        final Application app = applicationMapper.selectById(applicationId); // 查申请记录获取 tenantId
        final String companyName = dd.getCompanyName();                     // lambda 内需要 effectively final 变量
        if (app != null && app.getTenantId() != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 按 tenantId 查找企业用户（审核通过时创建的唯一预审用户）
                    List<User> users = userMapper.selectList(
                            new LambdaQueryWrapper<User>().eq(User::getTenantId, app.getTenantId()));
                    if (!users.isEmpty()) {
                        try {
                            String to = users.get(0).getEmail(); // 取第一个用户的邮箱
                            if (to != null && !to.isBlank()) {
                                mailService.sendDueDiligenceReturned(to, companyName, returnReason, loginUrl);
                            }
                        } catch (Exception e) {
                            // 邮件是旁路通知，发送失败不回滚事务，用户登录后仍能从页面看到退回状态
                            log.warn("尽调退回通知邮件发送失败, applicationId={}", applicationId, e);
                        }
                    }
                    // 站内通知超管：事务所已将尽调退回企业补充材料
                    notifyPlatform("APPLICATION_DD_RETURNED", "尽调已退回企业补充材料",
                            "【" + (dd.getDdFirmName() == null ? "事务所" : dd.getDdFirmName()) + "】退回【"
                                    + companyName + "】的尽调，原因：" + returnReason,
                            applicationId);
                }
            });
        }
    }

    /**
     * 更新单条核查清单项（事务所勾选/取消打勾 + 写备注）
     * <p>
     * 权限校验三重：
     * <ol>
     *   <li>核查项存在</li>
     *   <li>该项所属的尽调记录已分配给当前事务所</li>
     *   <li>尽调结果尚未提交（已提交后不可再修改核查清单）</li>
     * </ol>
     *
     * @param itemId  核查项ID
     * @param checked 是否通过（true=打勾，false=取消，null=不做改变）
     * @param remark  核查备注
     * @param firmId  事务所ID（从JWT获取，用于权限校验）
     * @throws BizException 核查项不存在、不属于当前事务所、或尽调已提交
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChecklistItem(Long itemId, Boolean checked, String remark, Long firmId) {
        // 校验：非事务所角色拒绝访问
        if (firmId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前用户非事务所账号");
        }
        // 1. 查核查项
        DdChecklist item = ddChecklistMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 2. 权限校验：通过核查项的 ddId 找到尽调记录，确认属于当前事务所
        DueDiligence dd = dueDiligenceMapper.selectById(item.getDdId());
        if (dd == null || dd.getDdFirmId() == null || !dd.getDdFirmId().equals(firmId)) {
            throw new BizException(ErrorCode.BAD_REQUEST); // 不属于当前事务所
        }

        // 3. 状态校验：已提交/已完成/已退回的尽调不可再修改核查清单
        if (DdFirmStatus.COMPLETED.getCode().equals(dd.getDdFirmStatus()) || DdFirmStatus.SUBMITTED.getCode().equals(dd.getDdFirmStatus())
                || DdFirmStatus.RETURNED.getCode().equals(dd.getDdFirmStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 4. 首次保存核查清单时，状态自动从 assigned → in_progress（表示事务所已开始核查）
        if (DdFirmStatus.ASSIGNED.getCode().equals(dd.getDdFirmStatus())) {
            dd.setDdFirmStatus(DdFirmStatus.IN_PROGRESS.getCode());
            dueDiligenceMapper.update(dd);
        }

        // 5. 更新核查项（checked + remark）
        item.setChecked(checked);
        item.setRemark(remark);
        ddChecklistMapper.update(item);
    }

    // ==================== 核查清单生成 ====================

    /**
     * 根据套餐类型动态生成核查清单项
     * <p>
     * 核查清单按业务场景分为5个分组（固定顺序）：
     * <ol>
     *   <li>企业信息核查 — 工商注册、法人背景、股权结构、社会责任</li>
     *   <li>法律诉讼核查 — 诉讼记录、行政处罚、国际合规</li>
     *   <li>财务信息核查 — 审计报告、银行信用、税务记录</li>
     *   <li>经营场所核查 — 实地走访、办公条件、仓储物流</li>
     *   <li>资质认证核查 — 企业资质、产品认证、知识产权</li>
     * </ol>
     * <p>
     * 套餐级别：
     * <ul>
     *   <li><b>basic</b>：每组1项基础核查</li>
     *   <li><b>standard</b>：每组1-2项</li>
     *   <li><b>premium</b>：每组2-3项，含最全面的核查</li>
     * </ul>
     * <p>
     * 注意：核查清单不区分买卖家角色，所有企业用同一套清单。
     *
     * @param packageType   套餐类型（basic/standard/premium）
     * @param applicantRole 申请身份（当前仅保留参数兼容，不区分买卖家）
     * @return 核查项列表，每项格式 [code, name, group]
     */
    private List<String[]> buildChecklistItems(String packageType, String applicantRole) {
        List<String[]> items = new ArrayList<>();
        boolean isStandard = "standard".equals(packageType);  // 标准版套餐
        boolean isPremium = "premium".equals(packageType);    // 高级版套餐

        // ---- 一、公司基本情况（所有套餐） ----
        items.add(new String[]{"biz_info", "工商注册信息已核实", "公司基本情况"});            // 基础：营业执照真伪
        items.add(new String[]{"legal_person", "法人代表背景无异常", "公司基本情况"});       // 基础：失信记录
        items.add(new String[]{"legal_record", "诉讼仲裁记录已核查", "公司基本情况"});       // 基础：裁判文书
        if (isStandard || isPremium) {
            items.add(new String[]{"administrative_penalty", "行政处罚记录已核查", "公司基本情况"}); // 中/高：行政处罚
            items.add(new String[]{"shareholder_change", "历次增资及股权转让已核查", "公司基本情况"}); // 中/高：沿革
            items.add(new String[]{"honor_license", "资质荣誉与经营许可已核验", "公司基本情况"});   // 中/高：资质许可
        }
        if (isPremium) {
            items.add(new String[]{"asset_ownership", "资产权属(土地/房产/知识产权)已核查", "公司基本情况"}); // 高：资产权属
            items.add(new String[]{"intl_compliance", "国际制裁与合规已筛查", "公司基本情况"});           // 高：制裁名单
        }

        // ---- 二、股东及实际控制人（standard + premium） ----
        if (isStandard || isPremium) {
            items.add(new String[]{"shareholder", "股权结构穿透至自然人已核查", "股东及实际控制人"}); // 中/高：股东穿透
        }
        if (isPremium) {
            items.add(new String[]{"controller_bg", "实际控制人背景已核查", "股东及实际控制人"}); // 高：实控人
            items.add(new String[]{"equity_pledge", "股权质押及代持已核查", "股东及实际控制人"}); // 高：质押代持
        }

        // ---- 三、董事监事高管（standard + premium） ----
        if (isStandard || isPremium) {
            items.add(new String[]{"dom_resume", "董监高简历已核查", "董事监事高管"}); // 中/高：董监高
        }
        if (isPremium) {
            items.add(new String[]{"dom_investment", "董监高对外投资及亲属关系已核查", "董事监事高管"}); // 高：亲属投资
        }

        // ---- 四、财务资料（所有套餐） ----
        items.add(new String[]{"financial", "审计报告/财务报表已审核", "财务资料"}); // 基础：财报
        if (isStandard || isPremium) {
            items.add(new String[]{"bank_credit", "银行征信报告已核查", "财务资料"}); // 中/高：征信
        }
        if (isPremium) {
            items.add(new String[]{"tax_record", "税务记录及税收优惠已核查", "财务资料"});      // 高：税务
            items.add(new String[]{"fund_flow", "与股东/董监高资金往来已核查", "财务资料"});    // 高：资金往来
            items.add(new String[]{"major_contracts", "重大销售/采购合同已核查", "财务资料"});   // 高：重大合同
        }

        // ---- 五、关联交易（仅 premium） ----
        if (isPremium) {
            items.add(new String[]{"related_transaction", "关联交易协议已核查", "关联交易"});   // 高：关联交易
            items.add(new String[]{"related_approval", "关联交易审批程序已核查", "关联交易"});   // 高：审批程序
        }

        // ---- 六、公司业务（standard + premium） ----
        if (isStandard || isPremium) {
            items.add(new String[]{"industry_status", "行业地位与市场竞争力已分析", "公司业务"}); // 中/高：行业地位
            items.add(new String[]{"biz_model", "经营模式与业务流程已核查", "公司业务"});       // 中/高：经营模式
        }
        if (isPremium) {
            items.add(new String[]{"rd_capability", "研发能力与核心技术已核查", "公司业务"}); // 高：研发
        }

        // ---- 七、公司治理（仅 premium） ----
        if (isPremium) {
            items.add(new String[]{"three_meetings", "三会运作已核查", "公司治理"});     // 高：三会运作
            items.add(new String[]{"internal_control", "内部控制制度已核查", "公司治理"}); // 高：内控
        }

        // ---- 八、尽调结论与建议（仅 premium） ----
        if (isPremium) {
            items.add(new String[]{"conclusion_advice", "尽调结论与上市可行性建议已出具", "尽调结论与建议"}); // 高：结论建议
        }

        return items;
    }

    // ==================== 事务所搜索 ====================

    /**
     * 搜索事务所列表（调用外部事务所 API，支持关键字模糊搜索）
     * <p>
     * 用于超管分配事务所时的下拉搜索。外部 API 调用有 try-catch 兜底，失败不阻塞业务流程。
     *
     * @param keyword 搜索关键字
     * @return 事务所信息列表，外部 API 异常时返回空列表
     */
    @Override
    public List<FirmInfo> searchFirms(String keyword) {
        return lawFirmClient.search(keyword);
    }

    // ==================== 内部契约（供应商域 Feign 调用） ====================

    /** 按申请ID查契约 DTO（不存在返回 null） */
    @Override
    public ApplicationInfoDTO getInfoById(Long id) {
        Application app = applicationMapper.selectById(id);
        return app == null ? null : toInfoDTO(app);
    }

    /** 按租户ID查契约 DTO（不存在返回 null） */
    @Override
    public ApplicationInfoDTO getInfoByTenantId(Long tenantId) {
        Application app = applicationMapper.selectByTenantId(tenantId);
        return app == null ? null : toInfoDTO(app);
    }

    /** 批量按租户ID查申请映射（tenantId → applicationId） */
    @Override
    public Map<Long, Long> getIdsByTenantIds(List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Long tenantId : tenantIds) {
            Application app = applicationMapper.selectByTenantId(tenantId);
            if (app != null) {
                map.put(tenantId, app.getId());
            }
        }
        return map;
    }

    /** 申请实体 → 契约 DTO（仅供应商域需要的字段子集） */
    private ApplicationInfoDTO toInfoDTO(Application app) {
        return ApplicationInfoDTO.builder()
                .id(app.getId())
                .tenantId(app.getTenantId())
                .applicantRole(app.getApplicantRole())
                .companyName(app.getCompanyName())
                .country(app.getCountry())
                .regNumber(app.getRegNumber())
                .establishedYear(app.getEstablishedYear())
                .contactName(app.getContactName())
                .contactTitle(app.getContactTitle())
                .contactEmail(app.getContactEmail())
                .contactPhone(app.getContactPhone())
                .buyCategory(app.getBuyCategory())
                .buyVolume(app.getBuyVolume())
                .buyVolumeAmount(app.getBuyVolumeAmount())
                .buyVolumeCurrency(app.getBuyVolumeCurrency())
                .fiscalPeriodStart(app.getFiscalPeriodStart())
                .fiscalPeriodEnd(app.getFiscalPeriodEnd())
                .buyMarket(app.getBuyMarket())
                .supCategory(app.getSupCategory())
                .supCapacity(app.getSupCapacity())
                .supCerts(app.getSupCerts())
                .supExport(app.getSupExport())
                .createTime(app.getCreateTime())
                .build();
    }

    // ==================== 尽调附件组装 ====================

    /**
     * 单条尽调 → VO（含子表多值附件），供单对象查询场景复用。
     *
     * @param dd 尽调实体
     * @return 附件已现签的 VO
     */
    private DueDiligenceVO toDdVO(DueDiligence dd) {
        List<DueDiligenceAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<DueDiligenceAttachment>()
                        .eq(DueDiligenceAttachment::getApplicationId, dd.getApplicationId())
                        .orderByAsc(DueDiligenceAttachment::getSortOrder));
        return DueDiligenceVO.fromEntity(dd, attachments, ossService);
    }

    /**
     * 批量按 applicationId 查子表附件并分组，避免逐条查询造成 N+1。
     *
     * @param applicationIds 申请ID列表
     * @return applicationId → 附件列表（保持 sort_order 升序）
     */
    private Map<Long, List<DueDiligenceAttachment>> loadAttachments(List<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Map.of();
        }
        List<DueDiligenceAttachment> all = attachmentMapper.selectList(
                new LambdaQueryWrapper<DueDiligenceAttachment>()
                        .in(DueDiligenceAttachment::getApplicationId, applicationIds)
                        .orderByAsc(DueDiligenceAttachment::getSortOrder));
        return all.stream().collect(java.util.stream.Collectors.groupingBy(DueDiligenceAttachment::getApplicationId));
    }
}
