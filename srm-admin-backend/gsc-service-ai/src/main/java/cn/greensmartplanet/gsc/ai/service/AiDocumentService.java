package cn.greensmartplanet.gsc.ai.service;

import cn.greensmartplanet.gsc.ai.dto.AiParentChunkDTO;
import cn.greensmartplanet.gsc.ai.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * AI 知识库文档服务接口 — 上传 / 列表 / 删除 / 重新解析
 *
 * @author gsc
 */
public interface AiDocumentService {

    /**
     * 上传文档：校验类型 → 落 OSS → 插 processing 记录 → 同步调 Python 解析 → 回写 ready/failed。
     *
     * @param file     上传文件（txt/md/pdf/docx/xlsx，≤10MB）
     * @param tenantId 租户 ID
     * @param userId   上传用户 ID
     * @return 文档最新状态 VO（ready/failed + chunkCount）
     * @throws IOException OSS 上传失败
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 文件为空、超限或类型不支持
     */
    DocumentVO upload(MultipartFile file, Long tenantId, Long userId) throws IOException;

    /**
     * 查当前租户的文档列表（create_time 倒序）
     *
     * @param tenantId 租户 ID
     * @return 文档 VO 列表（仅未删除）
     */
    List<DocumentVO> listDocuments(Long tenantId);

    /**
     * 删除文档：逻辑删记录 + 删 Qdrant 向量 + 删 OSS 原始文件（校验租户归属）。
     *
     * @param id       文档 ID
     * @param tenantId 租户 ID
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 文档不存在或不属于当前租户
     */
    void deleteDocument(Long id, Long tenantId);

    /**
     * 生成文档预览地址：校验租户归属后，按 oss_key 现签一个短效 URL 返回。
     * <p>
     * 不返回持久化 URL——签名会过期；每次预览都重新签，前端直接用它打开 iframe。
     *
     * @param id       文档 ID
     * @param tenantId 租户 ID
     * @return 短效预签名访问 URL
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 文档不存在或不属于当前租户
     */
    String previewUrl(Long id, Long tenantId);

    /**
     * 重新解析文档：对 failed 文档重新调 Python 切分向量化并回写状态（校验租户归属）。
     *
     * @param id       文档 ID
     * @param tenantId 租户 ID
     * @return 重试后的文档状态 VO
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 文档不存在或不属于当前租户
     */
    DocumentVO reparse(Long id, Long tenantId);

    /**
     * 按 parent_key 批量取父块（检索回填用；Python 内部回调）。
     * <p>
     * 不存在的 key 不返回（调用方回退子块原文）。
     *
     * @param tenantId 租户ID
     * @param keys     parent_key 列表
     * @return 命中的父块 DTO 列表
     */
    List<AiParentChunkDTO> getParentChunks(Long tenantId, List<String> keys);
}
