package cn.greensmartplanet.gsc.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表 VO — 前端会话列表展示
 *
 * @author gsc
 */
@Data
public class ConversationVO {

    /** 会话ID */
    private Long id;

    /** 会话标题 */
    private String title;

    /** 状态：active / archived */
    private String status;

    /** 更新时间（列表排序依据） */
    private LocalDateTime updateTime;
}
