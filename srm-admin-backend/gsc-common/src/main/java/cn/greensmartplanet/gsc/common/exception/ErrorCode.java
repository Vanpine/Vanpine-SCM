package cn.greensmartplanet.gsc.common.exception;

import lombok.Getter;

/**
 * 错误码枚举 — 项目中所有可能的错误，全在这里统一定义
 * <p>
 * 编码规则：
 *   1xxx = 系统级错误（500、404这类HTTP标准错误）
 *   2xxx = 认证相关（登录失败、Token过期、账号锁定、验证码）
 *   3xxx = 租户相关（租户不存在、已过期、平台租户不可删）
 *   4xxx = 用户相关（用户不存在、用户名重复）
 *   5xxx = 角色相关（角色不存在、系统角色不可删）
 *   6xxx = 入驻申请相关（申请已审核过）
 *   7xxx = 订阅/套餐相关（套餐不存在、订阅过期）
 *   8xxx = AI护照相关（护照校验失败）
 *   9xxx = 菜单/权限相关（菜单不存在、有子菜单不可删）
 *   10xxx = 智能撮合（采购需求/企业画像/匹配/对接）
 *   11xxx = 采购管理（询盘/报价/合同/授标）
 *   12xxx = 站内消息
 *   13xxx = 交付管理（交付单/发货/验收）
 *   14xxx = 可信交付（交付风险/补救）
 *   15xxx = AI 对话机器人（会话/知识库文档）
 * <p>
 * 用法：throw new BizException(ErrorCode.TENANT_NOT_FOUND);
 *
 * @author gsc
 */
@Getter
public enum ErrorCode {

    // ==================== 系统级 1xxx ====================
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ==================== 认证 2xxx ====================
    LOGIN_FAILED(2001, "用户名或密码错误"),
    ACCOUNT_LOCKED(2002, "账号已被锁定，请15分钟后重试"),
    ACCOUNT_DISABLED(2003, "账号已被禁用"),
    TOKEN_EXPIRED(2004, "Token已过期，请重新登录"),
    TOKEN_INVALID(2005, "Token无效"),
    PASSWORD_EXPIRED(2006, "密码已过期，请修改密码"),
    CAPTCHA_ERROR(2007, "验证码错误"),
    VERIFY_CODE_SENT(2008, "验证码已发送，请60秒后重试"),
    VERIFY_CODE_EXPIRED(2009, "验证码已过期，请重新获取"),
    VERIFY_CODE_ERROR(2010, "验证码错误"),

    // ==================== 租户 3xxx ====================
    TENANT_NOT_FOUND(3001, "租户不存在"),
    TENANT_CODE_EXISTS(3002, "租户编码已存在"),
    TENANT_DISABLED(3003, "所属租户已被停用，请联系管理员"),
    TENANT_EXPIRED(3004, "租户已过期"),
    TENANT_USER_LIMIT(3005, "租户用户数已达上限"),
    PLATFORM_TENANT_CANNOT_DELETE(3006, "平台租户不可删除"),
    TENANT_STATUS_INVALID(3007, "无效的租户状态"),

    // ==================== 用户 4xxx ====================
    USER_NOT_FOUND(4001, "用户不存在"),
    USERNAME_EXISTS(4002, "用户名已存在"),
    USER_DISABLED(4003, "账号已被禁用，请联系管理员"),
    OLD_PASSWORD_ERROR(4004, "原密码错误"),

    // ==================== 角色 5xxx ====================
    ROLE_NOT_FOUND(5001, "角色不存在"),
    ROLE_CODE_EXISTS(5002, "角色编码已存在"),
    SYSTEM_ROLE_CANNOT_DELETE(5003, "系统内置角色不可删除"),
    SYSTEM_ROLE_CANNOT_MODIFY(5004, "系统内置角色不可修改，如需调整请联系超管"),

    // ==================== 入驻申请 6xxx ====================
    APPLICATION_NOT_FOUND(6001, "入驻申请不存在"),
    APPLICATION_ALREADY_REVIEWED(6002, "该申请已审核过"),
    APPLICATION_DUPLICATE(6003, "您已提交过申请，请勿重复提交"),

    // ==================== 订阅 7xxx ====================
    PLAN_NOT_FOUND(7001, "套餐不存在"),
    SUBSCRIPTION_NOT_FOUND(7002, "订阅记录不存在"),
    SUBSCRIPTION_EXPIRED(7003, "订阅已过期"),
    DD_PACKAGE_NOT_FOUND(7004, "尽调套餐不存在或未定价"),
    DD_PAYMENT_CURRENCY_UNSUPPORTED(7005, "该币种暂未开通支付"),
    PAYMENT_CREATE_FAILED(7006, "创建支付单失败"),
    PAYMENT_NOTIFY_INVALID(7007, "支付回调无效"),
    PAYMENT_CHANNEL_CNY_ONLY(7008, "当前收款通道仅支持人民币（CNY）"),

    // ==================== AI护照 8xxx ====================
    PASSPORT_VERIFY_FAILED(8001, "AI护照身份校验失败"),
    PASSPORT_NOT_VERIFIED(8002, "请先完成AI世界护照实名认证"),
    PASSPORT_USER_NOT_FOUND(8003, "AI护照用户不存在"),

    // ==================== 菜单/权限 9xxx ====================
    MENU_NOT_FOUND(9001, "菜单不存在"),
    MENU_HAS_CHILDREN(9002, "该菜单下存在子菜单，请先删除子菜单"),

    // ==================== 智能撮合 10xxx ====================
    MATCH_REQUIREMENT_NOT_FOUND(10001, "采购需求不存在"),
    MATCH_PROFILE_NOT_FOUND(10002, "企业画像不存在，请等待尽调确认通过后自动初始化"),
    MATCH_ALREADY_CONNECTED(10003, "已向该供应商发起过对接，请勿重复操作"),
    MATCH_REQUIREMENT_CLOSED(10004, "该需求已关闭，无法操作"),
    MATCH_INVALID_STATE(10005, "当前状态不支持此操作"),
    MATCH_NOT_AUTHORIZED(10006, "无权操作该需求"),
    MATCH_ALREADY_APPLIED(10007, "您已申请过该需求，请等待采购商处理"),
    DUE_DILIGENCE_NOT_FOUND(10008, "未找到尽调记录"),

    // ==================== 采购管理 11xxx ====================
    PROCUREMENT_INQUIRY_NOT_FOUND(11001, "询盘不存在"),
    PROCUREMENT_QUOTE_NOT_FOUND(11002, "报价不存在"),
    PROCUREMENT_QUOTE_DUPLICATE(11003, "您已对该询盘提交过报价，可撤销后重新提交"),
    PROCUREMENT_CONTRACT_NOT_FOUND(11004, "合同不存在"),
    PROCUREMENT_NOT_INVITED(11005, "您未被邀请参与该询盘"),
    PROCUREMENT_STATUS_INVALID(11006, "当前状态不允许此操作"),
    PROCUREMENT_INVITE_NOT_FOUND(11007, "邀请记录不存在"),
    PROCUREMENT_INVITE_ALREADY_RESPONDED(11008, "您已响应该邀请，不可重复操作"),
    PROCUREMENT_AI_ANALYSIS_FAILED(11009, "AI分析失败，请稍后重试"),
    PROCUREMENT_CONTRACT_CANNOT_SIGN(11010, "待确认盖章件状态可签合同"),
    PROCUREMENT_CONTRACT_CANNOT_COMPLETE(11012, "仅已签状态可完成"),
    PROCUREMENT_CONTRACT_CANNOT_TERMINATE(11013, "当前状态不允许终止"),
    PROCUREMENT_TERMINATE_REASON_REQUIRED(11014, "终止原因不能为空"),
    PROCUREMENT_CONTRACT_DUPLICATE(11015, "该询盘已创建合同，不可重复创建"),
    PROCUREMENT_INQUIRY_CANNOT_PUBLISH(11016, "仅草稿状态可发布"),
    PROCUREMENT_INQUIRY_CANNOT_EDIT(11017, "已发布询盘不可编辑，可重新邀请"),
    PROCUREMENT_INQUIRY_CANNOT_CLOSE(11018, "仅已发布状态可关闭"),
    PROCUREMENT_INQUIRY_DUPLICATE_TITLE(11035, "该标题的询盘已存在，请直接追加供应商"),
    PROCUREMENT_INQUIRY_SUPPLIER_ALREADY_INVITED(11036, "该供应商已被邀请，请勿重复邀请"),
    PROCUREMENT_AWARD_QUANTITY_EXCEED(11037, "授标总量不能超过询盘采购数量"),
    PROCUREMENT_CONTRACT_AWARD_REQUIRED(11019, "询盘尚未授标，无法创建合同"),
    PROCUREMENT_CONTRACT_QUOTE_NOT_AWARDED(11020, "该报价未中标，无法创建合同"),
    PROCUREMENT_CONTRACT_DECLINE_REASON_REQUIRED(11021, "拒绝理由不能为空"),
    PROCUREMENT_CONTRACT_SIGN_ATTACHMENT_REQUIRED(11022, "盖章件不能为空"),
    PROCUREMENT_CONTRACT_CONFIRM_BY_OTHER(11023, "盖章件需由对方确认"),
    PROCUREMENT_CONTRACT_WINNING_QUOTE_REQUIRED(11024, "请选择中标供应商"),
    PROCUREMENT_CONTRACT_CANNOT_SUBMIT(11025, "仅草稿状态可发起合同"),
    PROCUREMENT_CONTRACT_CANNOT_CONFIRM(11026, "待供应商确认状态可确认条款"),
    PROCUREMENT_CONTRACT_CANNOT_DECLINE(11027, "待供应商确认状态可拒绝"),
    PROCUREMENT_CONTRACT_CANNOT_UPLOAD_SIGN(11028, "当前状态不允许上传盖章件"),
    PROCUREMENT_CONTRACT_SUPPLIER_NOT_CONFIRMED(11029, "请先由供应商确认条款后再上传盖章件"),
    PROCUREMENT_CONTRACT_CANNOT_WITHDRAW(11030, "待供应商确认状态可撤回"),
    PROCUREMENT_AWARD_WINNER_LOCKED(11031, "已中标供应商不可取消或修改中标数量"),
    PROCUREMENT_AWARD_QUANTITY_REQUIRED(11032, "授标数量必须大于0"),
    PROCUREMENT_CONTRACT_HAS_PENDING_DELIVERY(11033, "合同存在未验收的交付单，请全部验收后再完成"),
    PROCUREMENT_CONTRACT_BATCH_SUM_MISMATCH(11034, "各批数量之和必须等于合同总量"),

    // ==================== 站内消息 12xxx ====================
    NOTIFICATION_NOT_FOUND(12001, "通知不存在"),

    // ==================== 交付管理 13xxx ====================
    DELIVERY_NOT_FOUND(13001, "交付单不存在"),
    DELIVERY_STATUS_INVALID(13002, "当前交付状态不允许此操作"),
    DELIVERY_CONTRACT_HAS_ACTIVE(13003, "合同存在进行中的交付单，无法终止"),
    DELIVERY_RETURN_REASON_REQUIRED(13004, "退货原因不能为空"),
    DELIVERY_ATTACHMENT_REQUIRED(13005, "请上传凭证附件"),
    DELIVERY_DIFF_REMARK_REQUIRED(13006, "实收数量与发货数量不一致时，请填写差异说明"),
    DELIVERY_TRACKING_UNCHANGED(13007, "运单信息未变化"),
    DELIVERY_SHIP_QTY_EXCEED(13008, "发货数量不能超过本批待发数量"),
    DELIVERY_SHIP_QTY_TOTAL_EXCEED(13009, "累计发货数量已超过合同约定数量"),
    DELIVERY_CONTRACT_NOT_SIGNED(13010, "仅已签订的合同可创建交付单"),
    DELIVERY_CREATE_QTY_EXCEED(13011, "本批数量超过合同剩余可分配数量"),
    DELIVERY_DELETE_STATUS_INVALID(13012, "仅待发货的交付单可删除"),
    DELIVERY_DELETE_NOT_SUPPLIER(13013, "仅供应方可删除交付单"),
    DELIVERY_BATCH_ALREADY_SHIPPED(13014, "该批次已发货，不可重复发货"),
    DELIVERY_SHIP_QTY_MUST_EQUAL_BATCH(13015, "发货数量必须等于本批应发数量"),
    DELIVERY_RESHIP_QTY_INVALID(13016, "残次数量不合法，需大于0且小于实收数量"),
    DELIVERY_RESHIP_NOT_ALLOWED(13017, "仅普通交付单可发起部分残次补发"),
    DELIVERY_RESHIP_QTY_MUST_EQUAL(13018, "补发数量必须等于残次数量"),
    DELIVERY_ACCEPT_NEED_ARRIVED(13019, "物流未显示已到达，暂不能验收"),
    PROMISE_PENDING_EXISTS(13020, "该交付单已有待确认的承诺交期，请先处理"),
    PROMISE_DATE_NOT_FOUND(13021, "承诺交期记录不存在"),
    PROMISE_STATUS_INVALID(13022, "该承诺交期已处理，不能重复操作"),
    PROMISE_DATE_REQUIRED(13023, "承诺交期不能为空"),
    PROMISE_REASON_REQUIRED(13024, "承诺交期原因不能为空"),

    // ==================== 可信交付（交付风险/补救）14xxx ====================
    RISK_NOT_FOUND(14001, "交付风险不存在"),
    RISK_STATUS_INVALID(14002, "当前风险状态不允许此操作"),
    RISK_CLOSE_EVIDENCE_REQUIRED(14003, "关闭风险需提供补救回执或新的物流事件证明"),
    RISK_EVENT_REQUIRED(14004, "物流事件不能为空"),
    RECOVERY_ACTION_NOT_FOUND(14005, "补救工单不存在"),
    RECOVERY_ACTION_STATUS_INVALID(14006, "当前补救工单状态不允许此操作"),
    RECOVERY_RESULT_REQUIRED(14007, "完成补救工单需填写结果说明"),
    RISK_EVENT_TYPE_INVALID(14008, "物流事件类型不合法"),
    RISK_8D_EXISTS(14009, "该风险已发起8D整改，请勿重复发起"),
    RISK_8D_NOT_FOUND(14010, "8D整改报告不存在"),
    RISK_8D_STATUS_INVALID(14011, "当前8D状态不允许此操作"),
    RISK_8D_REJECT_REASON_REQUIRED(14012, "打回原因不能为空"),

    // ==================== AI 对话机器人 15xxx ====================
    AI_CONVERSATION_NOT_FOUND(15001, "会话不存在"),
    AI_DOCUMENT_NOT_FOUND(15002, "知识库文档不存在"),
    AI_DOCUMENT_TYPE_INVALID(15003, "仅支持 txt / md / pdf 格式"),
    AI_DOCUMENT_EMPTY(15004, "上传文件不能为空"),
    AI_DOCUMENT_PARSE_FAILED(15005, "文档解析失败，请稍后重试"),
    AI_SERVICE_UNAVAILABLE(15006, "AI 服务暂时不可用，请稍后重试"),
    AI_CHAT_IMAGE_NOT_FOUND(15007, "图片不存在"),
    AI_CHAT_IMAGE_TYPE_INVALID(15008, "仅支持 jpg / jpeg / png / webp 格式的图片"),
    AI_CHAT_IMAGE_TOO_LARGE(15009, "图片不能超过 10MB"),
    AI_CHAT_BUSY(15010, "该会话正在生成，请稍后再问"),
    AI_TOOL_UNKNOWN(15011, "不支持的 AI 工具"),
    AI_TOOL_ARG_INVALID(15012, "AI 工具参数不合法"),
    AI_TOOL_TOKEN_INVALID(15013, "AI 工具令牌无效或已过期"),
    AI_TOOL_PROPOSAL_NOT_FOUND(15014, "确认提案不存在或已过期，请重新发起"),
    AI_TOOL_PROPOSAL_INVALID(15015, "无权确认该提案"),
    AI_MESSAGE_NOT_FOUND(15016, "消息不存在"),
    AI_FEEDBACK_INVALID(15017, "反馈类型不合法");

    /** 错误码数字 */
    private final int code;
    /** 给前端看的错误提示信息 */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
