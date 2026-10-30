package cn.greensmartplanet.gsc.trade.projection;

import lombok.Data;

/**
 * 询盘按邀请状态分组的供应商数投影，用于列表页批量回填各类邀请计数。
 * <p>
 * 对应 InquiryInviteMapper.countByInquiryIdsGroupByStatus，SQL 按
 * inquiry_id + invite_status 分组统计供应商数量。
 * 。
 *
 * @author gsc
 */
@Data
public class InviteStatusCountRow {

    /**
     * 询盘 ID
     */
    private Long inquiryId;

    /**
     * 邀请状态：pending / accepted / declined
     */
    private String inviteStatus;

    /**
     * 该询盘下该状态对应的供应商数量
     */
    private Long supplierCount;
}
