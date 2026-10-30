package cn.greensmartplanet.gsc.ai.dto;

/**
 * 一次性 AI 工具令牌 — 从 tool token 解析出的身份上下文
 * <p>
 * Java 在调 Python /api/chat 时签发短效 token 并随请求体传给 Python，
 * Python 回调 /internal/ai/tool 时带回，Java 验签后据此恢复 tenantId/userId/assistantMessageId。
 * assistantMessageId 用于写工具生成提案时把流式阶段的占位消息ID记下来，确认/取消后覆盖成最终文案。
 * 采用 record（不可变值对象），避免工具执行链路上被意外篡改。
 *
 * @author gsc
 */
public record AiToolToken(Long tenantId, Long userId, Long assistantMessageId) {
}
