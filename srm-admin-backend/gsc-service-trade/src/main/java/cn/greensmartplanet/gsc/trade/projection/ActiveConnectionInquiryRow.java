package cn.greensmartplanet.gsc.trade.projection;

import lombok.Data;

/**
 * 对接记录与进行中询盘关联投影，用于批量判断哪些对接已有活跃询盘。
 * <p>
 * 对应 InquiryMapper.selectActiveByConnectionIds，一次批量查询返回对接 ID、
 * 命中的询盘 ID 和询盘状态，替代 Map 强转取值。一个对接可能命中多个进行中询盘
 * （一个询盘可邀请多家供应商），由调用方按 connectionId 归并。
 *
 * @author gsc
 */
@Data
public class ActiveConnectionInquiryRow {

    /** 对接记录 ID（connection.id） */
    private Long connectionId;

    /** 命中的进行中询盘 ID（inquiry.id） */
    private Long inquiryId;

    /** 询盘状态（open/quoting/awarded），用于标注“询价中” */
    private String inquiryStatus;
}
