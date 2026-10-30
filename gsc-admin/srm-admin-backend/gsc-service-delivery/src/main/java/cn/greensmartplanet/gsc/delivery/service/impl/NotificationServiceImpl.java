package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.mq.producer.NotificationPublisher;
import cn.greensmartplanet.gsc.delivery.mapper.NotificationMapper;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属交易/平台域，delivery 内为过渡副本（共享单库直查）；阶段 7/8 随桥接收敛。
 * 站内消息通知服务实现
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationPublisher notificationPublisher;

    /**
     * 发送站内消息：接收方为空则跳过，失败仅记日志不影响主业务
     * <p>阶段 8 切流：直写共享库改为发 gsc-notification 事件，由平台域消费落库。
     */
    @Override
    public void notify(Notification notification) {
        try {
            // 防御性校验：无接收方无法投递
            if (notification == null || notification.getReceiverTenantId() == null) {
                log.warn("站内消息发送跳过：通知对象为空或缺少接收方租户");
                return;
            }
            // 未读标记缺省补 0（与直写模式一致）
            if (notification.getIsRead() == null) {
                notification.setIsRead(0);
            }
            notificationPublisher.publish(notification);
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
