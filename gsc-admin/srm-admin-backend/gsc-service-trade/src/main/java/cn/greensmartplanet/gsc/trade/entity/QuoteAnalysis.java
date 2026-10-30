package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 比价分析结果缓存
 * <p>
 * 按 inquiry_id 唯一，每次 AI 分析后 upsert。
 * quote_snapshot 记录分析时的报价 ID 快照，GET 时动态对比判断是否过期。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quote_analysis")
public class QuoteAnalysis {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联询盘ID（唯一） */
    private Long inquiryId;

    /** AI 分析结果 JSON */
    private String analysisResult;

    /** 分析时的报价ID快照（逗号分隔，排序后） */
    private String quoteSnapshot;

    /** 分析时间 */
    private LocalDateTime analyzedAt;
}
