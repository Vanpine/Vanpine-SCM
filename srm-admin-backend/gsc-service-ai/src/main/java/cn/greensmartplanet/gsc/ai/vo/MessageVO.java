package cn.greensmartplanet.gsc.ai.vo;

import cn.greensmartplanet.gsc.ai.vo.ChatImageVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息 VO — 历史消息展示（含引用来源解析、图片回显）
 *
 * @author gsc
 */
@Data
public class MessageVO {

    /** 消息ID */
    private Long id;

    /** 角色：user / assistant / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用来源列表（assistant 消息，由 citations_json 解析） */
    private List<CitationVO> citations;

    /** 工具调用轨迹（assistant 消息，由 tool_calls_json 解析；展示「AI 查了什么」） */
    private List<ToolCallVO> toolCalls;

    /** 消息附带的图片（user 消息；previewUrl 查询时现签，不落库） */
    private List<ChatImageVO> images;

    /** 状态：complete / cancelled / error（streaming 占位不会返回） */
    private String status;

    /** 用户反馈：like / dislike，null 表示未反馈（前端据此回显👍/👎 选中态） */
    private String feedback;

    /** 点踩原因（仅 dislike 时有值） */
    private String feedbackReason;

    /** 创建时间 */
    private LocalDateTime createTime;
}
