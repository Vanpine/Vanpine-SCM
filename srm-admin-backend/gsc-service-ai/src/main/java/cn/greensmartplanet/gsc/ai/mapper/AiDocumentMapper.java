package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 知识库文档 Mapper
 * <p>
 * 多租户：手写 SQL 过滤 tenant_id + is_deleted = 0。
 *
 * @author gsc
 */
public interface AiDocumentMapper extends BaseMapper<AiDocument> {

    /**
     * 按租户查文档列表（create_time 倒序）
     */
    List<AiDocument> selectByTenant(@Param("tenantId") Long tenantId);

    /**
     * 校验文档归属（租户隔离）
     */
    AiDocument selectByIdAndTenant(@Param("id") Long id,
                                   @Param("tenantId") Long tenantId);
}
