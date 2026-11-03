package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryPromiseDate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交付承诺日期 Mapper
 *
 * @author gsc
 */
public interface DeliveryPromiseDateMapper extends BaseMapper<DeliveryPromiseDate> {

    /** 插入承诺记录 */
    int insert(DeliveryPromiseDate promise);

    /** 按ID查单条（逻辑删除过滤） */
    DeliveryPromiseDate selectById(@Param("id") Long id);

    /** 按交付单查承诺记录（时间正序，含已删除） */
    List<DeliveryPromiseDate> selectByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** 按交付单查待确认承诺（pending） */
    DeliveryPromiseDate selectPendingByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** 批量查交付单的待确认承诺（列表页回填标记，避免 N+1） */
    List<DeliveryPromiseDate> selectPendingByDeliveryIds(@Param("deliveryIds") List<Long> deliveryIds);

    /** 确认承诺：pending → accepted，记录确认人/时间 */
    int confirm(@Param("id") Long id,
                @Param("userId") Long userId);

    /** 拒绝承诺：pending → rejected，记录确认人/时间 */
    int reject(@Param("id") Long id,
               @Param("userId") Long userId);
}
