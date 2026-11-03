package cn.greensmartplanet.gsc.ai.service;

import cn.greensmartplanet.gsc.ai.vo.ChatImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * AI 对话图片服务 — 上传、归属校验、历史回显
 *
 * @author gsc
 */
public interface AiChatImageService {

    /**
     * 上传对话图片：校验类型/大小 → 上传 OSS → 落库 → 返回带短效预览地址的 VO
     *
     * @param file     图片文件
     * @param tenantId 租户ID
     * @param userId   上传用户ID
     * @return 图片 VO（含现签预览地址）
     */
    ChatImageVO upload(MultipartFile file, Long tenantId, Long userId);

    /**
     * 校验图片归属（租户 + 上传人）并返回 OSS key
     *
     * @param imageId  图片ID
     * @param tenantId 租户ID
     * @param userId   当前用户ID
     * @return OSS 对象路径
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 图片不存在或不属于当前用户
     */
    String getOwnedOssKey(Long imageId, Long tenantId, Long userId);

    /**
     * 按消息ID批量回显图片（租户过滤，现签预览地址）
     *
     * @param messageIds 消息ID列表
     * @param tenantId   租户ID
     * @return messageId → 图片 VO 列表
     */
    Map<Long, List<ChatImageVO>> listByMessageIds(List<Long> messageIds, Long tenantId);
}
