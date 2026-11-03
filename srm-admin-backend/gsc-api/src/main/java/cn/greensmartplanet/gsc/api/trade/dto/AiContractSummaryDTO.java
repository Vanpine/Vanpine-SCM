package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同摘要契约 DTO — 交易域内部接口返回给 AI 工具（list_contracts）的字段子集
 * <p>
 * 供 AI 回答「我有几份合同 / 什么状态 / 金额多少 / 何时交付」类问题。
 * 雪花主键出参由全局 Long→String 配置统一序列化为字符串（与 {@link ContractInfoDTO} 一致）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContractSummaryDTO {

    /** 合同ID（雪花主键，序列化为字符串） */
    private Long id;

    /** 合同编号 */
    private String contractNo;

    /** 合同标题 */
    private String title;

    /** 合同金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 合同状态：draft|pending_supplier|pending_confirm|signed|completed|terminated */
    private String status;

    /** 合同状态中文描述（草稿/待供应商确认/待确认盖章件/已签署/已完成/已终止），AI 回答展示用 */
    private String statusText;

    /** 采购方租户ID（雪花主键，序列化为字符串） */
    private Long buyerTenantId;

    /** 供应方租户ID（雪花主键，序列化为字符串） */
    private Long supplierTenantId;

    /** 约定交货日期 */
    private LocalDate deliveryDate;

    /** 签署日期（即合同生效日期） */
    private LocalDate signDate;
}
