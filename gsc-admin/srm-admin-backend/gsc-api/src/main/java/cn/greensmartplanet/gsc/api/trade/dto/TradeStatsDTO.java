package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交易域工作台统计契约 DTO — 平台域工作台 BFF 经 Feign 聚合的询盘/合同计数
 * <p>
 * 字段用 long 基本类型：缺失/降级时自然为 0，调用方无需判空。
 * 状态集合定义与原单体 DashboardServiceImpl 保持一致（open/quoting、pending_supplier 等）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeStatsDTO {

    /** 采购方：进行中询盘（open/quoting） */
    private long inquiryActive;
    /** 采购方：待比价询盘（quoting） */
    private long inquiryQuoting;
    /** 采购方：进行中合同（pending_supplier/pending_confirm/signed） */
    private long contractActiveBuyer;
    /** 采购方：合同待供应商确认（pending_supplier） */
    private long contractPendingSupplierBuyer;
    /** 采购方：合同待盖章（pending_confirm） */
    private long contractPendingConfirmBuyer;
    /** 供应方：进行中合同（pending_supplier/pending_confirm/signed） */
    private long contractActiveSupplier;
    /** 供应方：合同待确认条款（pending_supplier） */
    private long contractPendingSupplierSupplier;
    /** 供应方：合同待上传盖章件（pending_confirm） */
    private long contractPendingConfirmSupplier;
    /** 供应方：待报价询盘（邀请已接受 + 询盘在报价期 + 尚未提交有效报价） */
    private long pendingQuote;
    /** 供应方：已中标询盘数（按询盘去重） */
    private long awardBySupplier;
    /** 超管：平台合同总数（全租户） */
    private long contractAll;
}
