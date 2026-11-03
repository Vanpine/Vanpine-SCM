package cn.greensmartplanet.gsc.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI 工具调用请求体 — Python 侧 tool loop 回调 Java 工具 API 的入参
 * <p>
 * arguments 为模型生成的自由参数 Map，工具实现内部按 key 取 + 判空/类型转换兜底。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCallRequest {

    /** 工具名（必须已注册，未知抛 AI_TOOL_UNKNOWN） */
    private String toolName;

    /** 工具参数（key 为参数名，value 为模型生成的原始值） */
    private Map<String, Object> arguments;
}
