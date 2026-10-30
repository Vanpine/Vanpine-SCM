package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内消息通知 Mapper
 *
 * @author gsc
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 分页查我的通知（未读优先 + 时间倒序，支持仅未读筛选） */
    List<Notification> selectPage(@Param("tenantId") Long tenantId,
                                  @Param("unreadOnly") Boolean unreadOnly,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /** 统计我的通知总数 */
    long countList(@Param("tenantId") Long tenantId,
                   @Param("unreadOnly") Boolean unreadOnly);

    /** 未读数 */
    int countUnread(@Param("tenantId") Long tenantId);

    /** 标记单条已读（校验接收方租户，返回影响行数） */
    int markRead(@Param("id") Long id,
                 @Param("tenantId") Long tenantId);

    /** 全部标记已读 */
    int markAllRead(@Param("tenantId") Long tenantId);
}
