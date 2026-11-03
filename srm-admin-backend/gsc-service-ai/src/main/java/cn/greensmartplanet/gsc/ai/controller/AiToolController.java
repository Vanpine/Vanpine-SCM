package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.dto.AiToolCallRequest;
import cn.greensmartplanet.gsc.ai.dto.AiToolCallResponse;
import cn.greensmartplanet.gsc.ai.dto.AiToolToken;
import cn.greensmartplanet.gsc.ai.service.AiToolService;
import cn.greensmartplanet.gsc.ai.support.AiToolTokenService;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 工具执行入口 — Python 侧 tool loop 回调查业务数据的受控接口
 * <p>
 * 只对注册中心内服务可见（/internal/** 被网关 InternalBlockWebFilter 拦截），
 * Python 通过 AI 域中转调用，不直接连数据库。
 * <p>
 * 鉴权双路径：优先解析一次性工具令牌（Python 回调，身份来自 Java 签发的短效 JWT，
 * Python 无法伪造）；无令牌/解析失败回退到登录态（Phase 1 直连验证路径）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/ai/tool")
@RequiredArgsConstructor
@Tag(name = "AI 工具调用", description = "Function Calling 工具执行（仅服务间调用）")
public class AiToolController {

    private final AiToolService aiToolService;
    private final AiToolTokenService aiToolTokenService;

    /**
     * 执行一次 AI 工具调用。
     * <p>
     * 只读查询，无 @AuditLog；工具名/参数非法由 AiToolService 抛 BizException 统一处理。
     *
     * @param request       工具名 + 参数
     * @param authorization Bearer 头：优先为一次性工具令牌，其次为登录 JWT
     * @param user          登录态（工具令牌路径下为 null，由令牌提供身份）
     * @return 结构化执行结果
     */
    @Operation(summary = "执行 AI 工具（服务间调用）")
    @PostMapping("")
    public R<AiToolCallResponse> execute(@Valid @RequestBody AiToolCallRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String authorization,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        // 1. 优先解析一次性工具令牌（Python 回调路径）：身份随令牌而来，模型/中间服务无法伪造
        AiToolToken toolToken = tryParseToolToken(authorization);
        if (toolToken != null) {
            return R.ok(aiToolService.execute(request, toolToken.tenantId(), toolToken.userId(), toolToken.assistantMessageId()));
        }
        // 2. 回退登录态（Phase 1 直连验证路径）：令牌缺失/非工具令牌时按登录态鉴权，无前端消息
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return R.ok(aiToolService.execute(request, user.getTenantId(), user.getUserId(), null));
    }

    /**
     * 尝试解析一次性工具令牌：无 Bearer 头或非工具令牌返回 null，交由登录态路径处理。
     */
    private AiToolToken tryParseToolToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            return aiToolTokenService.parseBearer(authorization);
        } catch (BizException e) {
            // 令牌无效（过期/签名错/类型错）：不抛，回退登录态——无登录态时下游会统一 401
            return null;
        }
    }
}
