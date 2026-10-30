package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入驻申请实体 — 企业在AI世界护照验证后提交的入驻表单
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("application")
public class Application {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;                // 主键

    private String passportUuid;    // AI世界护照UUID（唯一，一人只能提交一次）
    private String applicantRole;   // 申请身份 buyer / supplier / buyer,supplier
    private String companyName;     // 公司名称
    private String country;         // 所在国家/地区
    private String regNumber;       // 企业注册号
    private Integer establishedYear;// 成立年份
    private String contactName;     // 联系人姓名
    private String contactTitle;    // 职位
    private String contactEmail;    // 联系邮箱
    private String contactPhone;    // 联系电话

    // ---- 采购方特有字段（申请身份含 buyer 时填写） ----
    private String buyCategory;     // 采购品类
    private String buyVolume;       // 年采购额（1-5档）
    private BigDecimal buyVolumeAmount;
    private String buyVolumeCurrency;
    private LocalDate fiscalPeriodStart;
    private LocalDate fiscalPeriodEnd;
    private String buyMarket;       // 目标市场

    // ---- 供应方特有字段（申请身份含 supplier 时填写） ----
    private String supCategory;     // 生产品类
    private String supCapacity;     // 年产能
    private String supCerts;        // 资质认证
    private String supExport;       // 出口经验（0-4档）

    // ---- 审核字段 ----
    private String status;          // pending_review / approved / rejected
    private String reviewComment;   // 审核意见（驳回时填写）
    private Long reviewedBy;        // 审核人ID
    private LocalDateTime reviewedAt;    // 审核时间
    private Long tenantId;          // 审核通过后创建的租户ID

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
