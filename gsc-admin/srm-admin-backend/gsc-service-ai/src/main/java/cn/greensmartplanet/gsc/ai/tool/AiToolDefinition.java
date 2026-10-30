package cn.greensmartplanet.gsc.ai.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 工具定义 — 工具名 + 参数 JSON schema + 描述
 * <p>
 * 命名对齐 Spring AI / LangChain4j 的 ToolDefinition 概念：一套供模型 Function Calling
 * 使用的结构化工具描述。parametersJson 为 OpenAI Function Calling 兼容的参数 schema
 * （type/description/required 字段），Python 侧直接拿去喂 DeepSeek。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolDefinition {

    /** 工具名（唯一，模型调用与注册表查找共用） */
    private String name;

    /** 工具用途描述（模型判断"何时该调这个工具"的依据） */
    private String description;

    /** 参数 JSON schema（OpenAI Function Calling 兼容格式） */
    private String parametersJson;
}
