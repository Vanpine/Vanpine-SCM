package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.delivery.entity.Notification;

import java.util.Map;

/**
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属交易/平台域，delivery 内为过渡副本（共享单库直查）；阶段 7/8 随桥接收敛。
 * 站内消息通知服务接口
 *
 * @author gsc
 */
public interface NotificationService {

    /** 发送通知（内部业务调用，失败仅记录日志不影响主流程） */
    void notify(Notification notification);

    /** 分页查我的通知（未读优先，可仅未读） */
    Map<String, Object> list(Long tenantId, int pageNum, int pageSize, Boolean unreadOnly);

    /** 未读数 */
    int countUnread(Long tenantId);

    /** 标记单条已读 */
    void markRead(Long id, Long tenantId);

    /** 全部标记已读 */
    void markAllRead(Long tenantId);
}
