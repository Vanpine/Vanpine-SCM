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

    /**
     * 按租户 + 内容哈希查未删除文档（重复上传去重）。
     * <p>
     * 依赖唯一索引 uk_tenant_active_hash 保证同租户同内容至多一条未删除记录。
     *
     * @param tenantId    租户ID
     * @param contentHash 文件内容 SHA-256（十六进制）
     * @return 命中的文档；不存在返回 null
     */
    AiDocument selectByTenantAndHash(@Param("tenantId") Long tenantId,
                                     @Param("contentHash") String contentHash);
}
