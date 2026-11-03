package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.dto.AiParentChunkDTO;
import cn.greensmartplanet.gsc.ai.entity.AiDocParentChunk;
import cn.greensmartplanet.gsc.ai.entity.AiDocument;
import cn.greensmartplanet.gsc.ai.mapper.AiDocParentChunkMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiDocumentMapper;
import cn.greensmartplanet.gsc.ai.service.AiDocumentService;
import cn.greensmartplanet.gsc.ai.vo.DocumentVO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 知识库文档服务实现
 * <p>
 * 上传流程（闭环）：校验类型 → 落 OSS → 插 processing 记录 → 同步调 Python 切分向量化 →
 * 回写 status(ready/failed) 与 chunk_count。
 * 删除：逻辑删文档 + 物理删分块 + 删 Qdrant 向量。
 * 重试解析：对 failed 文档重新走解析流程。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentServiceImpl implements AiDocumentService {

    private final AiDocumentMapper documentMapper;
    private final AiDocParentChunkMapper parentChunkMapper;
    private final OssService ossService;
    /** AI 服务客户端（RestClientConfig 预设 baseUrl = gsc.ai.service.url，直调 Python :8100） */
    private final RestClient aiRestClient;
    /** 解析响应里 parents 字段反序列化用（headingPath JSON 数组） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 支持的文档类型（与 ai_documents.file_type 枚举一致） */
    private static final Set<String> ALLOWED_TYPES = Set.of("txt", "md", "pdf", "docx", "xlsx", "xls");

    /** 上传大小上限（字节，10MB） */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    /** OSS 存储目录 */
    private static final String OSS_DIR = "ai-doc";

    /**
     * 上传并同步解析知识库文档。
     *
     * @param file     上传文件
     * @param tenantId 租户 ID
     * @param userId   上传用户 ID
     * @return 文档最新状态 VO
     * @throws IOException OSS 上传失败
     */
    @Override
    public DocumentVO upload(MultipartFile file, Long tenantId, Long userId) throws IOException {
        // 卫语句校验：空文件 / 超限文件 / 不支持类型直接失败，不进入 OSS 和解析链路
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.AI_DOCUMENT_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小不能超过 10MB");
        }
        String originalName = file.getOriginalFilename();
        String fileType = extractType(originalName);
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.AI_DOCUMENT_TYPE_INVALID);
        }

        // 1. 原始文件落 OSS，只保留 object key（不存签名 URL——签名会过期且无法续期）
        String ossKey = ossService.upload(file, OSS_DIR);

        // 2. 插 processing 记录（chunk_count 先置 0）
        AiDocument entity = AiDocument.builder()
                .tenantId(tenantId)
                .uploaderUserId(userId)
                .fileName(originalName)
                .fileType(fileType)
                .fileSize(file.getSize())
                .ossKey(ossKey)
                .chunkCount(0)
                .status("processing")
                .build();
        documentMapper.insert(entity);
        log.info("知识库文档已上传, id={}, fileName={}, tenantId={}", entity.getId(), originalName, tenantId);

        // 3. 同步调 Python 解析（下载 OSS → 切分 → 向量化），回写状态
        parseAndWriteback(entity);

        // 4. 重新查询，返回最新状态（含 chunk_count / status）
        // 解析结果由 parseAndWriteback 回写数据库，因此这里重查而不是直接返回内存对象
        AiDocument latest = documentMapper.selectByIdAndTenant(entity.getId(), tenantId);
        return toVO(latest);
    }

    /**
     * 查询当前租户知识库文档列表。
     *
     * @param tenantId 租户 ID
     * @return 文档 VO 列表（create_time 倒序）
     */
    @Override
    public List<DocumentVO> listDocuments(Long tenantId) {
        // SQL 已按 tenant_id + is_deleted 过滤，这里只做实体到展示对象转换
        return documentMapper.selectByTenant(tenantId).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 删除文档：逻辑删记录 + 删 Qdrant 向量 + 删 OSS 原始文件。
     *
     * @param id       文档 ID
     * @param tenantId 租户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id, Long tenantId) {
        // 归属校验：防越权删除他人文档
        AiDocument existing = documentMapper.selectByIdAndTenant(id, tenantId);
        if (existing == null) {
            throw new BizException(ErrorCode.AI_DOCUMENT_NOT_FOUND);
        }
        // 逻辑删文档 + 删父块（同事务）+ 删 Qdrant 向量
        documentMapper.deleteById(id);
        // 逻辑删该文档父块（与文档同事务，保证一致性）
        parentChunkMapper.deleteByDocument(id, tenantId);
        // 删 Qdrant 子块向量（Python 侧按 document_id + tenant_id 过滤删除；失败仅告警不阻断删除）
        deleteVectors(id, tenantId);
        // 删 OSS 原始文件：文件已无业务引用，不删会持续堆积占存储。
        // 放在最后且异常不阻断——即便 OSS 删除失败（网络抖动），文档在业务上已删除，
        // 残留对象无任何入口可访问，可后续手动清理；不能因删文件失败而回滚整个删除操作。
        try {
            ossService.deleteObject(existing.getOssKey());
        } catch (Exception e) {
            log.warn("删除 OSS 文件失败(不影响文档删除): id={}, ossKey={}", id, existing.getOssKey(), e);
        }
        log.info("知识库文档已删除, id={}, tenantId={}", id, tenantId);
    }

    /**
     * 生成文档预览地址（校验租户归属后按 oss_key 现签短效 URL）。
     *
     * @param id       文档 ID
     * @param tenantId 租户 ID
     * @return 短效预签名访问 URL
     */
    @Override
    public String previewUrl(Long id, Long tenantId) {
        // 归属校验：防越权预览他人文档（否则等于把 OSS 读权限开放给任意租户）
        AiDocument existing = documentMapper.selectByIdAndTenant(id, tenantId);
        if (existing == null) {
            throw new BizException(ErrorCode.AI_DOCUMENT_NOT_FOUND);
        }
        return ossService.generateGetUrl(existing.getOssKey());
    }

    /**
     * 重新解析失败文档。
     *
     * @param id       文档 ID
     * @param tenantId 租户 ID
     * @return 重试后的文档状态 VO
     */
    @Override
    public DocumentVO reparse(Long id, Long tenantId) {
        // 归属校验
        AiDocument existing = documentMapper.selectByIdAndTenant(id, tenantId);
        if (existing == null) {
            throw new BizException(ErrorCode.AI_DOCUMENT_NOT_FOUND);
        }
        // 先清旧向量与旧父块，再重跑解析，避免重复块
        deleteVectors(id, tenantId);
        parentChunkMapper.deleteByDocument(id, tenantId);
        existing.setStatus("processing");
        existing.setChunkCount(0);
        existing.setErrorMessage(null);
        documentMapper.updateById(existing);
        parseAndWriteback(existing);
        // 重查数据库，避免把中间态对象误认为最终状态
        return toVO(documentMapper.selectByIdAndTenant(id, tenantId));
    }

    /**
     * 同步调 Python /api/documents/parse，成功回写 ready + chunk_count，失败回写 failed + 错误信息。
     * <p>
     * 传给 Python 的是按 oss_key <b>现签的短效 URL</b>（10 分钟）——Python 用 urllib 直接 GET，
     * 签名自带授权，无需任何 OSS 凭证。解析是同步阻塞，10 分钟远大于单次解析耗时，不存在中途过期。
     * <p>
     * 失败不抛异常，而是落 failed 状态让前端可「重试」，避免一次上传直接报 500。
     */
    private void parseAndWriteback(AiDocument doc) {
        try {
            // 按 key 现签短效 URL 交给 Python 下载（不再复用库里存的长期 URL）
            String signedUrl = ossService.generateGetUrl(doc.getOssKey());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("document_id", doc.getId());
            body.put("tenant_id", doc.getTenantId());
            body.put("oss_url", signedUrl);
            body.put("file_name", doc.getFileName() == null ? "" : doc.getFileName());
            body.put("file_type", doc.getFileType());
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = aiRestClient.post()
                    .uri("/api/documents/parse")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Integer chunkCount = resp != null ? ((Number) resp.getOrDefault("chunk_count", 0)).intValue() : 0;
            // parent-child 父块：随解析响应回传，落 ai_doc_parent_chunks（Python 不再存 Qdrant）
            saveParentChunks(doc, resp);
            doc.setChunkCount(chunkCount);
            doc.setStatus("ready");
            doc.setErrorMessage(null);
            documentMapper.updateById(doc);
            log.info("文档解析成功: id={}, chunks={}", doc.getId(), chunkCount);
        } catch (Exception e) {
            log.error("文档解析失败: id={}, fileName={}", doc.getId(), doc.getFileName(), e);
            doc.setChunkCount(0);
            doc.setStatus("failed");
            doc.setErrorMessage("文档解析失败，请检查文件内容后重试");
            documentMapper.updateById(doc);
        }
    }

    /** 调 Python 删除文档向量（失败仅告警，不阻断主流程） */
    private void deleteVectors(Long documentId, Long tenantId) {
        try {
            aiRestClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/api/documents/{id}/vectors")
                            .queryParam("tenant_id", tenantId)
                            .build(documentId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 向量残留不影响文档逻辑删除；Qdrant 侧无跨租户泄露风险（检索按 tenant_id 过滤）
            log.warn("删除文档向量失败(可忽略): documentId={}, tenantId={}", documentId, tenantId, e);
        }
    }

    /** 从文件名提取扩展名（小写） */
    private String extractType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 从解析响应里取 parents 并落库（覆盖式：先删旧父块再插，保证重解析幂等）。
     * <p>
     * 父块落库失败不阻断解析主流程——子块已可检索，仅回填退化为子块原文。
     *
     * @param doc  文档实体（提供 documentId / tenantId）
     * @param resp Python 解析响应（含 parents 字段）
     */
    @SuppressWarnings("unchecked")
    private void saveParentChunks(AiDocument doc, Map<String, Object> resp) {
        if (resp == null) {
            return;
        }
        Object raw = resp.get("parents");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        try {
            // 覆盖式：先清该文档旧父块（重解析/换切分策略时不留脏数据）
            parentChunkMapper.deleteByDocument(doc.getId(), doc.getTenantId());
            List<AiDocParentChunk> entities = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                String parentKey = str(m.get("parent_key"));
                String content = str(m.get("content"));
                if (parentKey == null || parentKey.isBlank() || content == null) {
                    continue;
                }
                entities.add(AiDocParentChunk.builder()
                        .tenantId(doc.getTenantId())
                        .documentId(doc.getId())
                        .parentKey(parentKey)
                        .content(content)
                        .headingPath(toJson(m.get("heading_path")))
                        .tokenCount(intVal(m.get("token_count")))
                        .build());
            }
            for (AiDocParentChunk e : entities) {
                parentChunkMapper.insert(e);
            }
            log.info("父块已落库: documentId={}, parents={}", doc.getId(), entities.size());
        } catch (Exception e) {
            // 父块缺失只影响上下文丰富度，不影响子块检索；仅告警不阻断
            log.warn("父块落库失败(不影响检索): documentId={}", doc.getId(), e);
        }
    }

    /**
     * 按 parent_key 批量取父块（Python 检索回填回调）。
     */
    @Override
    public List<AiParentChunkDTO> getParentChunks(Long tenantId, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return parentChunkMapper.selectByKeys(tenantId, keys).stream()
                .map(e -> {
                    AiParentChunkDTO dto = new AiParentChunkDTO();
                    dto.setParentKey(e.getParentKey());
                    dto.setContent(e.getContent());
                    dto.setHeadingPath(fromJson(e.getHeadingPath()));
                    dto.setTokenCount(e.getTokenCount());
                    return dto;
                })
                .toList();
    }

    /** 任意对象 → 字符串（null 安全，非字符串转 toString） */
    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** 任意对象 → int（null / 非数字 → 0） */
    private Integer intVal(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** 任意对象 → JSON 字符串（List 序列化；null → null） */
    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 字符串 → List<String>（解析失败 → 空列表） */
    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 实体 → 文档 VO */
    private DocumentVO toVO(AiDocument entity) {
        DocumentVO vo = new DocumentVO();
        vo.setId(entity.getId());
        vo.setFileName(entity.getFileName());
        vo.setFileType(entity.getFileType());
        vo.setFileSize(entity.getFileSize());
        vo.setChunkCount(entity.getChunkCount());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
