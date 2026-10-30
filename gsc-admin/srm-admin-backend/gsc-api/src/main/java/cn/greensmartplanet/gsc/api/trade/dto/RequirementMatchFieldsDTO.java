package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购需求智能匹配字段契约 DTO — 交易域内部接口返回给供应商域匹配策略的字段子集
 * <p>
 * 供应商域智能匹配策略（AiMatchStrategy/RuleBasedMatchStrategy）入参，
 * 替代跨服务直查 procurement_requirement 表。
 * 契约约定：需求不存在返回 data=null（HTTP 200），调用方按 null 处理业务分支。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementMatchFieldsDTO {

    /** 需求ID */
    private Long id;
    /** 发布方租户ID（排除自己匹配自己） */
    private Long tenantId;
    /** 需求标题 */
    private String title;
    /** 采购品类 */
    private String category;
    /** 资质要求 */
    private String certRequire;
    /** 地域要求 */
    private String region;
    /** 需求详细描述 */
    private String description;
    /** 采购数量 */
    private String quantity;
    /** 单位 */
    private String unit;
    /** 预算 */
    private String budget;
    /** 状态 */
    private String status;
    /** 截止日期（广场展示） */
    private LocalDate deadline;
    /** 发布时间（广场展示） */
    private LocalDateTime createTime;
    /** 目标供应商数 */
    private Integer targetSupplierCount;
    /** 是否公开到广场 */
    private Boolean isPublic;
}
