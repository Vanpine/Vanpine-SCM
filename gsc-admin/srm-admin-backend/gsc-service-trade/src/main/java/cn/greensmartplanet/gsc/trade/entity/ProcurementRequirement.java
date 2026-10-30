package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购需求实体
 * <p>
 * 采购方发布的采购需求，品类/地域/资质等字段用于 AI 智能寻源匹配。
 * status 字段：open（进行中）、closed（手动关闭）、expired（过期，查询时动态判定）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("procurement_requirement")
public class ProcurementRequirement {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 发布方租户ID */
    private Long tenantId;

    /** 需求标题 */
    private String title;

    /** 采购品类（逗号分隔文本，用于 AI 语义匹配） */
    private String category;

    /** 采购数量描述 */
    private String quantity;

    /** 单位（如：件、吨、套） */
    private String unit;

    /** 期望供应商地域（空=不限，用于匹配过滤） */
    private String region;

    /** 资质要求文本（空=不限，用于 AI 语义解析） */
    private String certRequire;

    /** 截止日期 */
    private LocalDate deadline;

    /** 预算描述 */
    private String budget;

    /** 需求详细描述（富文本，AI 语义分析核心字段） */
    private String description;

    /** 状态：open/closed（expired 为查询时动态判定，不入库） */
    private String status;

    /** 目标供应商数量（null=不限数量，达标后显示"已完成"） */
    private Integer targetSupplierCount;

    /** 是否公开到需求广场 */
    private Boolean isPublic;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    // ---- 审计 ----
    /** 创建人用户ID */
    private Long createBy;

    /** 更新人用户ID */
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
