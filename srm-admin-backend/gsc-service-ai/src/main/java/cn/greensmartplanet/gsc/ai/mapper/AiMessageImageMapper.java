package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiMessageImage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 消息-图片关联 Mapper
 *
 * @author gsc
 */
public interface AiMessageImageMapper extends BaseMapper<AiMessageImage> {

    /**
     * 按消息ID批量查关联（供消息历史回显，租户过滤）
     */
    List<AiMessageImage> selectByMessageIds(@Param("messageIds") List<Long> messageIds,
                                            @Param("tenantId") Long tenantId);
}
