package cn.greensmartplanet.gsc.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 消息反馈请求体
 *
 * @author gsc
 */
@Data
public class FeedbackRequestDTO {

    /** 反馈类型：like / dislike（必填） */
    @NotBlank(message = "反馈类型不能为空")
    private String feedback;

    /** 点踩原因（dislike 时可带；like 忽略） */
    private String reason;
}
