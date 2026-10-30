package cn.greensmartplanet.gsc.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 父块 DTO — Python 解析响应回传 / Java 内部接口返回的父块契约
 * <p>
 * 跨服务传输用契约 DTO（禁止 Map），字段与 Python 侧 parents 元素一一对应。
 *
 * @author gsc
 */
@Data
public class AiParentChunkDTO {

    /** 文档内父块确定性 key（UUID5），子块 payload.parent_id 指向它 */
    private String parentKey;

    /** 父块全文 */
    private String content;

    /** 章节路径（有序，如 ["合同", "第三条 付款方式"]） */
    private List<String> headingPath;

    /** 父块 token 估算数 */
    private Integer tokenCount;
}
