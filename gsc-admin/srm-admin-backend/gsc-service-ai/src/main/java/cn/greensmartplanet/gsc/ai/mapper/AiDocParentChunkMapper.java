package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiDocParentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 知识库父块 Mapper
 * <p>
 * 多租户：手写 SQL 过滤 tenant_id + is_deleted = 0。
 *
 * @author gsc
 */
public interface AiDocParentChunkMapper extends BaseMapper<AiDocParentChunk> {

    /**
     * 按 parent_key 批量取父块（检索回填用；租户隔离）。
     *
     * @param tenantId 租户ID
     * @param keys     parent_key 列表
     * @return 命中的父块（不存在的 key 不返回，调用方回退子块原文）
     */
    List<AiDocParentChunk> selectByKeys(@Param("tenantId") Long tenantId,
                                        @Param("keys") List<String> keys);

    /**
     * 按文档ID逻辑删除该文档全部父块（删除文档 / 重解析前清理）。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID
     * @return 影响行数
     */
    int deleteByDocument(@Param("documentId") Long documentId,
                         @Param("tenantId") Long tenantId);
}
