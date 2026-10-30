package cn.greensmartplanet.gsc.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单张历史图片的内容描述 — 随历史消息下发给 Python，替代原图。
 * <p>
 * 为什么按张存而不是拼成一整段：一条消息最多 4 张图，用户问「第 2 张」时
 * 拼成一整段模型分不清指哪张。带上顺序（列表下标）才能对上「第 N 张」。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDescriptionDTO {

    /**
     * 该图在本条消息中的序号（1 起），与用户看到的顺序一致。
     * <p>
     * 必须显式带序号而不是靠列表下标：某张图生成描述失败时会被跳过，
     * 用下标会让「第 2 张」错位到实际第 3 张。
     */
    private Integer index;

    /**
     * 图片ID（雪花，序列化为字符串防 JS 精度丢失）。
     * 模型用不到，仅供排查「这段描述对应哪张图」。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long imageId;

    /** 图片内容描述（Java 侧首次使用时由多模态模型生成） */
    private String description;
}
