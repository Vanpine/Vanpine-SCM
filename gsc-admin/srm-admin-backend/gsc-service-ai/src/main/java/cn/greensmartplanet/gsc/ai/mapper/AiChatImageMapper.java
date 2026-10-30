package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiChatImage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 对话图片资产 Mapper
 * <p>
 * 多租户：查询手写 SQL 过滤 tenant_id + is_deleted = 0。
 *
 * @author gsc
 */
public interface AiChatImageMapper extends BaseMapper<AiChatImage> {

    /**
     * 校验图片归属（租户 + 上传人，防越权引用他人图片）
     */
    AiChatImage selectByIdAndOwner(@Param("id") Long id,
                                   @Param("tenantId") Long tenantId,
                                   @Param("uploaderUserId") Long uploaderUserId);

    /**
     * 按图片ID批量查（供消息历史回显，租户过滤）
     */
    List<AiChatImage> selectByIdsAndTenant(@Param("ids") List<Long> ids,
                                           @Param("tenantId") Long tenantId);
}
