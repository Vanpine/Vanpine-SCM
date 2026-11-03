package cn.greensmartplanet.gsc.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建会话请求体
 *
 * @author gsc
 */
@Data
public class CreateConversationDTO {

    /** 会话标题（可选；不传时前端/后端用首条消息自动生成） */
    private String title;
}
