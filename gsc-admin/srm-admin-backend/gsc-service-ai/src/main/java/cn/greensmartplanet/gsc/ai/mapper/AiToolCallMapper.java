package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiToolCall;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * AI 工具调用审计 Mapper
 * <p>
 * 仅追加写（BaseMapper.insert），无自定义查询：审计统计走 BI / 报表侧直接查表。
 *
 * @author gsc
 */
public interface AiToolCallMapper extends BaseMapper<AiToolCall> {
}
