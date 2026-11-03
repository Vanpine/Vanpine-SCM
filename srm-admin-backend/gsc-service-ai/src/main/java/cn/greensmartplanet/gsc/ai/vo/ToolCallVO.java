package cn.greensmartplanet.gsc.ai.vo;

import lombok.Data;

/**
 * 工具调用轨迹 VO — 一条「AI 调用了某工具」的记录（历史消息展示用）
 * <p>
 * 由 ai_messages.tool_calls_json 解析而来；用户在历史里能看到「AI 查了什么、成没成功」，
 * 建立对答案来源的信任。审计明细另见 ai_tool_calls 表。
 *
 * @author gsc
 */
@Data
public class ToolCallVO {

    /** 工具名（如 list_contracts） */
    private String tool;

    /** 是否成功 */
    private Boolean success;

    /** 结果摘要（如「已找到 3 条结果」） */
    private String summary;

    /** 耗时（毫秒） */
    private Integer costMs;

    /** 调用参数（原样透传的 JSON，前端按需展示） */
    private Object arguments;
}
