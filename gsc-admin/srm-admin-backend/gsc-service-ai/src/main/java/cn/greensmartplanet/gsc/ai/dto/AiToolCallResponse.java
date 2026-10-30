package cn.greensmartplanet.gsc.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 工具调用响应体 — 回喂 Python 侧 tool loop 的结构化结果
 * <p>
 * success=false 时 error 带用户可读的错误文案（由 Python 转成自然语言或直接提示）。
 * result 为工具返回的结构化数据（供应商列表 / 供应商详情 / 合同列表）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCallResponse {

    /** 是否执行成功 */
    private boolean success;

    /** 工具返回的结构化数据（成功时有值，可为空列表/null） */
    private Object result;

    /** 失败原因（成功时为 null） */
    private String error;

    /** 是否需要二次确认（写工具为 true，Python 据此短路返回 confirm_required 事件） */
    private boolean requireConfirm;

    /** 待确认提案ID（requireConfirm=true 时前端回传，用于确认落库） */
    private String proposalId;

    /** 操作摘要（requireConfirm=true 时前端展示"确认记录备注：xxx"） */
    private String actionSummary;

    /**
     * 本次工具涉及的业务对象（读工具=结果集，写工具=被操作对象）。
     * Python 原样放进 tool_result / confirm_required 事件回传，Java 落会话上下文供下一轮指代消解；
     * 无对象时为空列表。
     */
    private List<ContextEntityDTO> entities;
}
