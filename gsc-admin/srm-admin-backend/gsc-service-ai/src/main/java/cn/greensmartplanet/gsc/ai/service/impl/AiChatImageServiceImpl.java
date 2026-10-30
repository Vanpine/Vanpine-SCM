package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.entity.AiChatImage;
import cn.greensmartplanet.gsc.ai.entity.AiMessageImage;
import cn.greensmartplanet.gsc.ai.mapper.AiChatImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.support.AiChatImageDescService;
import cn.greensmartplanet.gsc.ai.vo.ChatImageVO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话图片服务实现
 * <p>
 * 安全要点：
 * <ul>
 *   <li>类型按文件头魔数判定，不信任文件名与 MultipartFile.getContentType()</li>
 *   <li>OSS key 用服务端生成的 UUID 名，原始文件名只落库展示，不参与路径拼接</li>
 *   <li>只落 oss_key，签名 URL 每次使用现签（10 分钟有效）</li>
 * </ul>
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatImageServiceImpl implements AiChatImageService {

    private final AiChatImageMapper chatImageMapper;
    private final AiMessageImageMapper messageImageMapper;
    private final OssService ossService;
    private final AiChatImageDescService imageDescService;

    /** 单图大小上限：10MB */
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    /** OSS 存储目录 */
    private static final String OSS_DIR = "ai-chat";

    /** PNG 魔数 */
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * 上传对话图片。
     */
    @Override
    public ChatImageVO upload(MultipartFile file, Long tenantId, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.AI_DOCUMENT_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.AI_CHAT_IMAGE_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传图片失败: size={}", file.getSize(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }

        // 按文件头判定真实类型（jpg/jpeg/png/webp），伪造扩展名会在这一步被拒
        String ext = detectImageExt(bytes);
        if (ext == null) {
            throw new BizException(ErrorCode.AI_CHAT_IMAGE_TYPE_INVALID);
        }

        // OSS 安全文件名：chat-image-{uuid}.{ext}；原始文件名只落库展示
        String safeName = "chat-image-" + java.util.UUID.randomUUID() + "." + ext;
        String ossKey;
        try {
            ossKey = ossService.upload(bytes, OSS_DIR, safeName);
        } catch (IOException e) {
            log.error("图片上传 OSS 失败: ext={}, size={}", ext, bytes.length, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }

        AiChatImage entity = AiChatImage.builder()
                .tenantId(tenantId)
                .uploaderUserId(userId)
                .ossKey(ossKey)
                .fileName(file.getOriginalFilename() == null ? safeName : file.getOriginalFilename())
                .contentType(contentTypeOf(ext))
                .fileSize((long) bytes.length)
                .status("ready")
                .build();
        chatImageMapper.insert(entity);

        // 上传即异步生成描述：图只有在成为「历史轮次」后才用得上描述，
        // 此刻触发有整整一个来回的提前量，用户下一轮追问「刚才那张图」时描述已就绪。
        // 放到「图变成历史」时才触发会晚两轮。失败由按历史消息的兜底路径重试。
        // 注：本方法无 @Transactional，insert 已自动提交，异步线程能查到。
        imageDescService.ensureAsyncForImages(List.of(entity.getId()), tenantId);

        log.info("对话图片上传成功: imageId={}, ext={}, size={}", entity.getId(), ext, bytes.length);
        return toVO(entity);
    }

    /**
     * 校验归属并返回 OSS key。
     */
    @Override
    public String getOwnedOssKey(Long imageId, Long tenantId, Long userId) {
        AiChatImage image = chatImageMapper.selectByIdAndOwner(imageId, tenantId, userId);
        if (image == null) {
            throw new BizException(ErrorCode.AI_CHAT_IMAGE_NOT_FOUND);
        }
        return image.getOssKey();
    }

    /**
     * 按消息ID批量回显图片。
     */
    @Override
    public Map<Long, List<ChatImageVO>> listByMessageIds(List<Long> messageIds, Long tenantId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiMessageImage> relations = messageImageMapper.selectByMessageIds(messageIds, tenantId);
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> imageIds = relations.stream().map(AiMessageImage::getImageId).distinct().toList();
        List<AiChatImage> images = chatImageMapper.selectByIdsAndTenant(imageIds, tenantId);
        // 一次现签，避免同一图片被多条关联重复签名
        Map<Long, ChatImageVO> imageMap = new LinkedHashMap<>();
        for (AiChatImage img : images) {
            imageMap.put(img.getId(), toVO(img));
        }

        Map<Long, List<ChatImageVO>> result = new LinkedHashMap<>();
        for (AiMessageImage rel : relations) {
            ChatImageVO vo = imageMap.get(rel.getImageId());
            if (vo != null) {
                result.computeIfAbsent(rel.getMessageId(), k -> new ArrayList<>()).add(vo);
            }
        }
        return result;
    }

    /**
     * 实体 → VO（现签短效预览地址，不落库）。
     */
    private ChatImageVO toVO(AiChatImage entity) {
        ChatImageVO vo = new ChatImageVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setFileName(entity.getFileName());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setPreviewUrl(ossService.generateGetUrl(entity.getOssKey()));
        return vo;
    }

    /**
     * 按文件头魔数判定图片类型，返回扩展名；无法识别返回 null。
     * <p>
     * 支持：JPEG(FF D8 FF) / PNG(89 50 4E 47 0D 0A 1A 0A) / WebP(RIFF....WEBP)
     */
    private String detectImageExt(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        boolean png = true;
        for (int i = 0; i < MAGIC_PNG.length; i++) {
            if (b[i] != MAGIC_PNG[i]) {
                png = false;
                break;
            }
        }
        if (png) {
            return "png";
        }
        // WebP: "RIFF" .... "WEBP"（第 0-3 字节 RIFF，第 8-11 字节 WEBP）
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /**
     * 扩展名 → 标准 MIME 类型
     */
    private String contentTypeOf(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
