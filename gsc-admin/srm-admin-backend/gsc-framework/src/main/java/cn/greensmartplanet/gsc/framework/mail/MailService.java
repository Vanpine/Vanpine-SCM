package cn.greensmartplanet.gsc.framework.mail;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import com.aliyun.dm20151123.Client;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.teaopenapi.models.Config;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * 邮件发送服务 — 阿里云 DirectMail API
 * <p>
 * 微服务拆分：由 GscFrameworkAutoConfiguration 注册（配置 spring.mail.aliyun.* 才生效）。
 */
@Slf4j
public class MailService {

    @Value("${spring.mail.aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${spring.mail.aliyun.access-key-secret}")
    private String accessKeySecret;

    @Value("${spring.mail.aliyun.account-name}")
    private String accountName;

    @Value("${spring.mail.aliyun.from-alias}")
    private String fromAlias;

    private Client client;

    @PostConstruct
    public void init() throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "dm.aliyuncs.com";
        client = new Client(config);
        log.info("阿里云 DirectMail 客户端初始化完成");
    }

    /**
     * 发送入驻审核通过通知
     *
     * @param to          收件人邮箱
     * @param companyName 公司名称
     * @param tenantCode  租户编码（登录时使用）
     * @param username    管理员账号
     * @param password    管理员密码
     * @param loginUrl    登录地址
     */
    public void sendApprovalNotice(String to, String companyName, String tenantCode,
                                   String username, String password, String loginUrl) {
        try {
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 入驻申请已通过")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#1a1a2e'>入驻申请已通过</h2>" +
                                    "<p>恭喜！<b>%s</b> 的入驻申请已审核通过，您的企业管理后台已开通。</p>" +
                                    "<div style='background:#f8fafc;border-radius:8px;padding:16px 20px;margin:20px 0'>" +
                                    "<p style='margin:8px 0'><b>登录地址：</b>%s</p>" +
                                    "<p style='margin:8px 0'><b>租户编码：</b><code style='background:#e2e8f0;padding:2px 8px;border-radius:4px'>%s</code></p>" +
                                    "<p style='margin:8px 0'><b>管理员账号：</b>%s</p>" +
                                    "<p style='margin:8px 0'><b>初始密码：</b>%s</p>" +
                                    "</div>" +
                                    "<p style='color:#ef4444;font-size:13px'>请尽快登录并修改密码。</p>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", companyName, loginUrl, tenantCode, username, password));
            client.singleSendMail(request);
            log.info("入驻审核通过通知已发送至 {}", to);
        } catch (Exception e) {
            log.error("审核通知邮件发送失败: {}", to, e);
        }
    }

    /**
     * 发送入驻审核驳回通知
     * @param to          收件人邮箱
     * @param companyName 公司名称
     * @param reason      驳回原因
     */
    public void sendRejectionNotice(String to, String companyName, String reason, String websiteUrl) {
        try {
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 入驻申请审核结果")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#1a1a2e'>入驻申请审核结果</h2>" +
                                    "<p><b>%s</b> 的入驻申请未能通过审核。</p>" +
                                    "<div style='background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:16px 20px;margin:20px 0'>" +
                                    "<p style='margin:0;color:#991b1b'><b>驳回原因：</b>%s</p>" +
                                    "</div>" +
                                    "<p style='color:#64748b;font-size:13px'>您可以根据驳回原因修改资料后重新提交申请。</p>" +
                                    "<a href='%s' target='_blank' style='display:inline-block;margin-top:12px;padding:12px 28px;background:#2563eb;color:#fff;text-decoration:none;border-radius:8px;font-size:14px'>前往官网重新提交</a>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", companyName, reason, websiteUrl));
            client.singleSendMail(request);
            log.info("入驻审核驳回通知已发送至 {}", to);
        } catch (Exception e) {
            log.error("驳回通知邮件发送失败: {}", to, e);
        }
    }

    /**
     * 发送密码重置验证码
     *
     * @param to   收件人邮箱
     * @param code 6位验证码
     */
    public void sendResetCode(String to, String code) {
        try {
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 密码重置验证码")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#1a1a2e'>GLOBAL SCM</h2>" +
                                    "<p>您正在重置平台登录密码，验证码如下：</p>" +
                                    "<p style='font-size:32px;font-weight:700;letter-spacing:6px;color:#2563eb;margin:20px 0'>%s</p>" +
                                    "<p style='color:#94a3b8;font-size:13px'>有效期 5 分钟，如非本人操作请忽略。</p>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", code));

            client.singleSendMail(request);
            log.info("密码重置验证码已发送至 {}", to);
        } catch (Exception e) {
            log.error("邮件发送失败: {}", to, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "邮件发送失败，请稍后重试");
        }
    }

    /**
     * 发送事务所账号通知（分配事务所以后，发送登录凭证）
     */
    public void sendFirmCredentials(String to, String firmName, String username, String password, String loginUrl) {
        try {
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 事务所尽调账号已开通")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#1a1a2e'>事务所尽调账号已开通</h2>" +
                                    "<p>贵所已被分配尽调任务，请使用以下账号登录 SRM 平台完成尽调核查：</p>" +
                                    "<div style='background:#f0f9ff;border:1px solid #bae6fd;border-radius:8px;padding:16px 20px;margin:20px 0'>" +
                                    "<p style='margin:0 0 8px'><b>事务所：</b>%s</p>" +
                                    "<p style='margin:0 0 8px'><b>登录账号：</b>%s</p>" +
                                    "<p style='margin:0'><b>登录密码：</b>%s</p>" +
                                    "</div>" +
                                    "<p style='color:#64748b;font-size:13px'>首次登录后请尽快修改密码。</p>" +
                                    "<a href='%s' target='_blank' style='display:inline-block;margin-top:12px;padding:12px 28px;background:#2563eb;color:#fff;text-decoration:none;border-radius:8px;font-size:14px'>前往登录</a>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", firmName, username, password, loginUrl));
            client.singleSendMail(request);
            log.info("事务所账号通知已发送至 {}, firmName={}", to, firmName);
        } catch (Exception e) {
            log.error("事务所账号通知发送失败: {}", to, e);
        }
    }

    /**
     * 发送尽调完成通知（事务所提交结果后，告知企业尽调已通过，可登录后台管理）
     *
     * @param to          收件人邮箱
     * @param companyName 公司名称
     * @param loginUrl    后台登录地址
     */
    public void sendDueDiligenceCompleted(String to, String companyName, String loginUrl) {
        try {
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 尽调已完成，您的企业管理后台已开通")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#1a1a2e'>尽调已完成</h2>" +
                                    "<p>恭喜！<b>%s</b> 的尽职调查已通过事务所审核，您的企业管理后台已全面开通。</p>" +
                                    "<p style='color:#64748b;font-size:13px'>您现在拥有企业管理员权限，可登录后台进行用户管理、角色分配等操作。</p>" +
                                    "<a href='%s' target='_blank' style='display:inline-block;margin-top:12px;padding:12px 28px;background:#2563eb;color:#fff;text-decoration:none;border-radius:8px;font-size:14px'>前往登录</a>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", companyName, loginUrl));
            client.singleSendMail(request);
            log.info("尽调完成通知已发送至 {}, companyName={}", to, companyName);
        } catch (Exception e) {
            log.error("尽调完成通知发送失败: {}", to, e);
        }
    }

    /**
     * 尽调退回补充通知 — 事务所发现问题退回给企业，企业需登录补充材料后重新提交
     *
     * @param to           收件人邮箱
     * @param companyName  公司名称
     * @param returnReason 事务所退回原因
     * @param loginUrl     后台登录地址
     */
    public void sendDueDiligenceReturned(String to, String companyName, String returnReason, String loginUrl) {
        try {
            // 构建邮件请求：红色主题提示用户需要补充材料
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 尽调资料需补充，请登录完善")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#dc2626'>尽调资料需补充</h2>" +
                                    "<p><b>%s</b> 的尽职调查已被事务所退回，需要您补充材料后重新提交审核。</p>" +
                                    // 红色高亮框展示退回原因，用户一眼看到需要做什么
                                    "<div style='background:#fef2f2;border-left:4px solid #dc2626;padding:12px 16px;margin:16px 0;font-size:14px;color:#991b1b'>" +
                                    "<b>退回原因：</b>%s</div>" +
                                    "<p style='color:#64748b;font-size:13px'>请登录后台进入尽调页面，根据退回原因补充或修正相应材料。保存后系统将自动通知事务所重新核查。</p>" +
                                    "<a href='%s' target='_blank' style='display:inline-block;margin-top:12px;padding:12px 28px;background:#dc2626;color:#fff;text-decoration:none;border-radius:8px;font-size:14px'>前往登录补充</a>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", companyName, returnReason, loginUrl));
            client.singleSendMail(request);
            log.info("尽调退回通知已发送至 {}, companyName={}", to, companyName);
        } catch (Exception e) {
            // 邮件发送失败仅记日志，不抛异常（调用方在 afterCommit 回调中，抛异常会导致事务回滚）
            log.error("尽调退回通知发送失败: {}", to, e);
        }
    }

    /**
     * 企业重新提交通知 — 企业补充材料后通知事务所重新核查
     *
     * @param to          收件人邮箱（事务所账号邮箱）
     * @param companyName 企业名称
     * @param firmName    事务所名称
     * @param loginUrl    后台登录地址
     */
    public void sendDueDiligenceResubmitted(String to, String companyName, String firmName, String loginUrl) {
        try {
            // 构建邮件：通知事务所企业已补充材料，可重新核查
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(accountName)
                    .setFromAlias(fromAlias)
                    .setAddressType(1)
                    .setReplyToAddress(true)
                    .setToAddress(to)
                    .setSubject("GLOBAL SCM - 企业已重新提交尽调资料，请核查")
                    .setHtmlBody(String.format(
                            "<div style='max-width:480px;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,sans-serif'>" +
                                    "<h2 style='color:#2563eb'>企业已重新提交资料</h2>" +
                                    "<p><b>%s</b> 已根据退回要求补充或修正了尽调材料，现已重新提交，请登录工作台进行核查。</p>" +
                                    "<p style='color:#64748b;font-size:13px'>事务所：%s</p>" +
                                    "<a href='%s' target='_blank' style='display:inline-block;margin-top:12px;padding:12px 28px;background:#2563eb;color:#fff;text-decoration:none;border-radius:8px;font-size:14px'>前往工作台核查</a>" +
                                    "<hr style='border:none;border-top:1px solid #e8ecf1;margin:24px 0'>" +
                                    "<p style='color:#94a3b8;font-size:12px'>GreenSmartPlanet · GLOBAL SCM 团队</p>" +
                                    "</div>", companyName, firmName, loginUrl));
            client.singleSendMail(request);
            log.info("企业重新提交通知已发送至 {}, companyName={}", to, companyName);
        } catch (Exception e) {
            // 邮件发送失败仅记日志，不抛异常
            log.error("企业重新提交通知发送失败: {}", to, e);
        }
    }
}
