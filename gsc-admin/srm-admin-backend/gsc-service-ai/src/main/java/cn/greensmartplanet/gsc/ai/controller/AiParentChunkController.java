package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.dto.AiParentChunkDTO;
import cn.greensmartplanet.gsc.ai.dto.AiToolToken;
import cn.greensmartplanet.gsc.ai.service.AiDocumentService;
import cn.greensmartplanet.gsc.ai.support.AiToolTokenService;
import cn.greensmartplanet.gsc.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 知识库父块回填入口 — Python 检索命中的子块按 parent_key 回查父块全文
 * <p>
 * 只对注册中心内服务可见（/internal/** 被网关拦截），Python 通过 AI 域中转调用，不直连数据库。
 * 鉴权复用 AI 工具令牌（与 /internal/ai/tool 同一套）：Python 回调带当轮 tool_token，
 * Java 验签拿 tenantId——Python 无签名密钥，无法伪造租户身份。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/ai/parents")
@RequiredArgsConstructor
@Tag(name = "AI 知识库父块", description = "parent-child 父块回填（仅服务间调用）")
public class AiParentChunkController {

    private final AiDocumentService documentService;
    private final AiToolTokenService aiToolTokenService;

    /**
     * 按 parent_key 批量取父块。
     * <p>
     * 只读查询，无 @AuditLog；令牌非法由 AiToolTokenService 抛 BizException 统一处理。
     *
     * @param body          {"parent_keys": [...]}（租户身份随令牌，忽略 body 里的 tenant_id，防越权）
     * @param authorization Bearer 工具令牌
     * @return 命中的父块列表（不存在的 key 不返回，调用方回退子块原文）
     */
    @Operation(summary = "批量取父块（服务间调用）")
    @PostMapping("")
    public R<List<AiParentChunkDTO>> query(@RequestBody Map<String, Object> body,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 身份只认令牌（不信任 body 里的 tenant_id，防跨租户越权取父块）
        AiToolToken token = aiToolTokenService.parseBearer(authorization);
        @SuppressWarnings("unchecked")
        List<String> keys = body == null ? List.of()
                : (List<String>) body.getOrDefault("parent_keys", List.of());
        // 单次回调上限保护：超长列表截断，防异常请求拖垮查询
        if (keys.size() > MAX_KEYS) {
            keys = keys.subList(0, MAX_KEYS);
        }
        return R.ok(documentService.getParentChunks(token.tenantId(), keys));
    }

    /** 单次取父块上限（一次检索的父块数远小于此） */
    private static final int MAX_KEYS = 50;
}
