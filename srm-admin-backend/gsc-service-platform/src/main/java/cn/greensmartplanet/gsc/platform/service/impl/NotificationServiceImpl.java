package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.platform.entity.Notification;
import cn.greensmartplanet.gsc.platform.mapper.NotificationMapper;
import cn.greensmartplanet.gsc.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内消息通知服务实现
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    /** 发送站内消息：接收方为空则跳过，失败仅记日志不影响主业务 */
    @Override
    public void notify(Notification notification) {
        try {
            if (notification == null || notification.getReceiverTenantId() == null) {
                log.warn("站内消息发送跳过：通知对象为空或缺少接收方租户");
                return;
            }
            if (notification.getIsRead() == null) {
                notification.setIsRead(0);
            }
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 通知失败不影响主业务，仅记录日志（与尽调第三方推送"失败仅 log 不回滚"一致）
            log.error("站内消息发送失败: type={}, receiverTenantId={}",
                    notification != null ? notification.getType() : null,
                    notification != null ? notification.getReceiverTenantId() : null, e);
        }
    }

    /** 分页查站内消息列表，支持只看未读 */
    @Override
    public Map<String, Object> list(Long tenantId, int pageNum, int pageSize, Boolean unreadOnly) {
        int offset = (pageNum - 1) * pageSize;
        List<Notification> list = notificationMapper.selectPage(tenantId, unreadOnly, offset, pageSize);
        long total = notificationMapper.countList(tenantId, unreadOnly);
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 统计未读消息数（前端红点角标用） */
    @Override
    public int countUnread(Long tenantId) {
        return notificationMapper.countUnread(tenantId);
    }

    /** 单条标记已读（校验归属，非本人消息抛异常） */
    @Override
    public void markRead(Long id, Long tenantId) {
        int rows = notificationMapper.markRead(id, tenantId);
        if (rows == 0) {
            throw new BizException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    /** 全部标记已读 */
    @Override
    public void markAllRead(Long tenantId) {
        notificationMapper.markAllRead(tenantId);
    }
}
