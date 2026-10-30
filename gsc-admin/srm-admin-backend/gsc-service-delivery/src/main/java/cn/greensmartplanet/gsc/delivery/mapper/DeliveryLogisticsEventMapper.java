package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryLogisticsEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交付物流事件 Mapper
 *
 * @author gsc
 */
public interface DeliveryLogisticsEventMapper extends BaseMapper<DeliveryLogisticsEvent> {

    /** 插入物流事件 */
    int insert(DeliveryLogisticsEvent event);

    /** 按交付单查物流事件时间线（时间正序） */
    List<DeliveryLogisticsEvent> selectByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** 按交付单查最新一条物流事件 */
    DeliveryLogisticsEvent selectLatestByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** 按交付单查最新一条带 ETA 的事件 */
    DeliveryLogisticsEvent selectLatestEtaByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** 按交付单+类型查是否存在指定事件（去重/判断清关滞留用） */
    int countByDeliveryAndType(@Param("deliveryId") Long deliveryId,
                               @Param("eventType") String eventType);

    /** 批量查交付单最新一条物流事件（列表页回填，避免逐单 N+1） */
    List<DeliveryLogisticsEvent> selectLatestByDeliveryIds(@Param("deliveryIds") List<Long> deliveryIds);

    /** 批量查交付单最新一条带 ETA 的事件（列表页回填） */
    List<DeliveryLogisticsEvent> selectLatestEtaByDeliveryIds(@Param("deliveryIds") List<Long> deliveryIds);

    /** 批量查存在指定类型事件的交付单ID集合（风险扫描清关阻塞判定，替代逐单 count） */
    List<Long> selectDeliveryIdsWithEventType(@Param("deliveryIds") List<Long> deliveryIds,
                                              @Param("eventType") String eventType);
}
