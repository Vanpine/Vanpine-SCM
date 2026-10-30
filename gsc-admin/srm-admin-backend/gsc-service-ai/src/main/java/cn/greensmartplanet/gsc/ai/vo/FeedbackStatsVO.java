package cn.greensmartplanet.gsc.ai.vo;

import lombok.Data;

/**
 * AI 反馈统计 VO — 供运维看板/接口查看回答质量信号
 * <p>
 * 显式反馈（like/dislike）与行为信号（cancelled=用户中途停止）合并返回，
 * 让「用户没点赞但直接停掉」这类强信号也能被看见。
 *
 * @author gsc
 */
@Data
public class FeedbackStatsVO {

    /** 点赞数 */
    private Integer likeCount;

    /** 点踩数 */
    private Integer dislikeCount;

    /** 已反馈总数（like + dislike） */
    private Integer feedbackCount;

    /** 中途停止数（status=cancelled，隐式负反馈） */
    private Integer cancelledCount;

    /** 正常完成回答数（status=complete） */
    private Integer answerCount;
}
