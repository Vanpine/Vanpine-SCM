package cn.greensmartplanet.gsc.ai.service;

import cn.greensmartplanet.gsc.ai.dto.AiToolCallRequest;
import cn.greensmartplanet.gsc.ai.dto.AiToolCallResponse;
import cn.greensmartplanet.gsc.ai.dto.AiToolConfirmResult;
import cn.greensmartplanet.gsc.ai.dto.AiToolProposal;

/**
 * AI 工具执行服务接口 — 校验工具名并分发到对应工具
 * <p>
 * 工具执行需要当前登录身份（tenantId/userId），由 AiToolController 从
 * {@code @AuthenticationPrincipal} 提取后传入，模型无法伪造（防越权，红线 2）。
 * <p>
 * 写工具（如 create_supplier_note）不直接落库：生成「待确认提案」返回给 Python，
 * 由用户在确认接口点击确认后才真正落库（二次确认 + 审计）。
 *
 * @author gsc
 */
public interface AiToolService {

    /**
     * 执行一次工具调用。
     *
     * @param request            工具名 + 参数
     * @param tenantId           当前租户（登录态，工具据此隔离数据）
     * @param userId             当前用户（登录态，用于审计/归属）
     * @param assistantMessageId 本轮 assistant 消息ID（写工具生成提案时记录，确认/取消后覆盖占位）
     * @return 结构化执行结果
     */
    AiToolCallResponse execute(AiToolCallRequest request, Long tenantId, Long userId, Long assistantMessageId);

    /**
     * 确认并执行一个写操作提案（前端确认按钮触发，落库 + 审计）。
     * <p>
     * 返回最终展示文案 + 关联的 assistant 消息ID，供上层把流式阶段的
     * 「已发起操作，请确认执行。」占位覆盖成最终文案。
     *
     * @param proposalId 提案ID（前端回传）
     * @param tenantId   当前登录租户
     * @param userId     当前登录用户
     * @return 确认回执（finalText + assistantMessageId）
     */
    AiToolConfirmResult confirm(String proposalId, Long tenantId, Long userId);

    /**
     * 取消一个写操作提案（前端取消按钮触发，不落库）。
     * <p>
     * 消费掉 Redis 提案（防事后误确认），并返回取消文案 + 关联的 assistant 消息ID。
     *
     * @param proposalId 提案ID（前端回传）
     * @param tenantId   当前登录租户
     * @param userId     当前登录用户
     * @return 取消回执（finalText + assistantMessageId）
     */
    AiToolConfirmResult cancel(String proposalId, Long tenantId, Long userId);
}
