package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.dto.AssignFirmDTO;
import cn.greensmartplanet.gsc.platform.dto.FirmInfo;
import cn.greensmartplanet.gsc.platform.dto.ReviewApplicationDTO;
import cn.greensmartplanet.gsc.platform.dto.SubmitApplicationDTO;
import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;
import cn.greensmartplanet.gsc.platform.entity.DueDiligence;
import cn.greensmartplanet.gsc.platform.vo.ApplicationVO;
import cn.greensmartplanet.gsc.platform.vo.DueDiligenceVO;

import java.util.List;
import java.util.Map;

/**
 * 入驻申请服务接口 — 尽调全流程的核心 Service
 * <p>
 * 负责以下业务流程：
 * <ol>
 *   <li><b>入驻申请</b>：官网提交 → AI护照校验 → 实名检查 → 查重 → 入库</li>
 *   <li><b>审核</b>：通过 → 创建租户 + 管理员 + 尽调记录；驳回 → 邮件通知</li>
 *   <li><b>尽调</b>：用户填表单 → 支付 → 分配事务所 → 事务所核查 → 提交结果</li>
 *   <li><b>核查清单</b>：按套餐动态生成，事务所逐项勾选</li>
 * </ol>
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本类含尽调寄生逻辑（读写 due_diligence/dd_checklist 表），阶段 7 gsc-service-supplier 上线后改 Feign 调用收敛。</p>
 * @author gsc
 */
public interface ApplicationService {

    /**
     * 分页查询入驻申请（超管后台列表页）
     * <p>
     * keyword 模糊匹配公司名/联系人/邮箱，status 精确筛选审核状态。
     * DD 过滤参数（paymentStatus 等）通过 LEFT JOIN due_diligence 过滤。
     * 返回数据中包含批量附加的尽调信息。
     *
     * @param pageNum       页码（从1开始）
     * @param pageSize      每页条数
     * @param keyword       模糊搜索关键字
     * @param status        审核状态筛选
     * @param applicantRole 申请身份筛选（buyer/supplier）
     * @param packageType   套餐类型筛选（basic/standard/premium）
     * @param paymentStatus 支付状态筛选（unpaid/paid）
     * @param ddFirmStatus  事务所状态筛选（unassigned/assigned/in_progress/submitted/returned/completed）
     * @param ddReportStatus 报告状态筛选（pending/submitted/completed）
     * @return {records, total, page, pageSize}
     */
    Map<String, Object> page(int pageNum, int pageSize, String keyword, String companyName, String contactName,
                             String status, String applicantRole, String packageType,
                             String paymentStatus, String ddFirmStatus, String ddReportStatus);

    /**
     * 入驻申请详情（含尽调数据）
     *
     * @param id 申请ID
     * @return 申请详情VO（含尽调数据）
     */
    ApplicationVO detail(Long id);

    /**
     * 审核入驻申请（超管操作，核心流程）
     * <p>
     * 通过：校验租户编码唯一性 → 创建租户 + 管理员账号（角色=预审用户） → 发送通知邮件
     * → 更新申请状态 → 创建尽调记录（拷贝重叠字段作为初始值）
     * <p>
     * 驳回：发送驳回通知邮件（含审核意见） → 更新申请状态
     *
     * @param id         申请ID
     * @param dto        审核参数（status, tenantCode, adminUsername, adminPassword, reviewComment）
     * @param reviewerId 审核人ID
     */
    void review(Long id, ReviewApplicationDTO dto, Long reviewerId);

    /**
     * 提交入驻申请（官网公开接口，无需登录）
     * <p>
     * 流程：AI护照校验 → 实名认证检查 → 查重（同passport不可重复提交）
     * → 组装实体 → 驳回重提交走UPDATE，新申请走INSERT
     *
     * @param dto 前端提交的入驻申请数据
     * @return {applicationId, status, createdAt}
     */
    Map<String, Object> submit(SubmitApplicationDTO dto);

    /**
     * 根据租户ID查询入驻申请（尽调页面用）
     * <p>
     * 登录用户通过 JWT 中的 tenantId 查到自己企业的申请 + 尽调数据。
     *
     * @param tenantId 租户ID
     * @return 申请详情VO（含尽调），不存在返回 null
     */
    ApplicationVO getByTenantId(Long tenantId);

    /**
     * 创建尽调支付单：调中台下单拿收款码，只存单号，状态仍是 unpaid。
     * 已支付只能由 {@link #handlePaymentNotify} 写入，点「去支付」不会改成 paid。
     */
    cn.greensmartplanet.gsc.platform.vo.CreatePaymentVO createPayment(Long id, String packageType, String paymentCurrency);

    /**
     * 工行/中台异步回调入账。
     * 用 {@code out_trade_no} 对尽调单，确认 {@code return_code=0} 后把
     * {@code payment_status} 改为 paid；事务提交后再回写中台 {@code /paid}。
     *
     * @param body 已拆开 biz_content 的回调字段（至少含 out_trade_no）
     */
    void handlePaymentNotify(com.fasterxml.jackson.databind.JsonNode body);

    /**
     * 查尽调支付状态，给收款码页轮询。
     * 只读库，不调中台、不改状态。
     *
     * @param applicationId 入驻申请 ID
     * @return paid / unpaid，没有尽调记录时为 null
     */
    String getPaymentStatus(Long applicationId);

    /**
     * 保存尽调扩展信息（用户在尽调页面自行填写）
     * <p>
     * 校验通用必填字段（省份、城市、营业执照、法人信息）后，有则更新无则新增。
     *
     * @param id 申请ID
     * @param dd 前端提交的尽调实体
     */
    void updateDdInfo(Long id, DueDiligence dd);

    /**
     * 分配事务所（超管操作）
     * <p>
     * 流程：更新尽调记录 → 初始化核查清单 → 创建/查找事务所账号 → 发邮件通知
     *
     * @param applicationId 申请ID
     * @param dto           分配参数（firmId, firmName, contactEmail, contactName, contactPhone, deadline, instruction）
     */

    void assignFirm(Long applicationId, AssignFirmDTO dto);

    /**
     * 事务所工作台数据（FIRM 角色登录后看到的已分配企业列表）
     *
     * @param firmId 事务所ID
     * @return 尽调VO列表（含核查清单）
     */
    List<DueDiligenceVO> getFirmWorkbench(Long firmId);

    /**
     * 事务所提交尽调结果
     * <p>
     * 校验权限（尽调属于当前事务所）→ 解析报告文件参数（url|filename）→ 更新状态=completed
     *
     * @param applicationId 申请ID
     * @param firmId        事务所ID（用于权限校验）
     * @param progress      事务所工作备注
     * @param resultUrl     报告文件参数（格式：OSS URL|原始文件名）
     */
    void submitFirmResult(Long applicationId, Long firmId, String progress, String resultUrl);

    /**
     * 事务所退回尽调给企业补充材料（状态 assigned → returned）
     * <p>
     * 前置校验：尽调存在 + 归属当前事务所 + 状态为 assigned。
     * 退回后企业在尽调页面看到退回原因，补充材料后状态自动回到 assigned。
     *
     * @param applicationId 申请ID
     * @param firmId        事务所ID（用于权限校验）
     * @param returnReason  退回原因（必填，告知企业需要补充什么）
     */
    void returnToEnterprise(Long applicationId, Long firmId, String returnReason);

    /**
     * 管理员确认尽调通过 — 只允许超管调用
     * <p>
     * submitted → completed：更新状态 + 升级用户角色(DUE_DILIGENCE→TENANT_ADMIN) + 发邮件通知企业。
     * 这是"事务所提交→管理员确认"两步流程的第二步，角色升级和邮件通知都在此执行。
     *
     * @param applicationId 申请ID
     * @param reviewerId    审核人ID（当前登录的超管）
     */
    void confirmDdComplete(Long applicationId, Long reviewerId);

    /**
     * 更新单条核查清单项（事务所勾选/取消 + 备注）
     * <p>
     * 三重权限校验：核查项存在 → 属于当前事务所 → 尽调尚未提交
     *
     * @param itemId  核查项ID
     * @param checked 是否通过
     * @param remark  备注
     * @param firmId  事务所ID（用于权限校验）
     */
    void updateChecklistItem(Long itemId, Boolean checked, String remark, Long firmId);

    /**
     * 搜索事务所列表（调用外部事务所 API，支持关键字模糊搜索）
     *
     * @param keyword 搜索关键字
     * @return 事务所信息列表
     */
    List<FirmInfo> searchFirms(String keyword);

    // ==================== 内部契约（供应商域 Feign 调用） ====================

    /** 按申请ID查契约 DTO（不存在返回 null） */
    ApplicationInfoDTO getInfoById(Long id);

    /** 按租户ID查契约 DTO（不存在返回 null） */
    ApplicationInfoDTO getInfoByTenantId(Long tenantId);

    /** 批量按租户ID查申请映射（tenantId → applicationId） */
    Map<Long, Long> getIdsByTenantIds(List<Long> tenantIds);
}
