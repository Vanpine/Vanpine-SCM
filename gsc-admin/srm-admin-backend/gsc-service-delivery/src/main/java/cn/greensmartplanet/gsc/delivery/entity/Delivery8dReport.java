package cn.greensmartplanet.gsc.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 8D 整改报告实体
 * <p>
 * 供应商出现严重问题时，采购方发起 8D 整改，供应商按 D1-D8 八步书面回复，
 * 采购方审核通过才闭环。一个风险一张 8D（uk_risk_id 防重）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_8d_report")
public class Delivery8dReport {

    /** 主键（雪花 ID：Service 层 IdWorker 生成，与 delivery_promise_date 同款） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联交付风险ID */
    private Long riskId;

    /** 状态：pending 待填写 / submitted 待审核 / approved 已通过 / rejected 已打回 */
    private String status;

    /** 整改期限（发起时设，默认 7 天） */
    private LocalDate deadline;

    /** D1 组建团队 */
    private String d1Team;

    /** D2 问题描述 */
    private String d2Desc;

    /** D3 临时围堵措施 */
    private String d3Containment;

    /** D4 根本原因分析 */
    private String d4RootCause;

    /** D5 永久纠正措施 */
    private String d5Corrective;

    /** D6 实施与验证 */
    private String d6Verification;

    /** D7 预防再发 */
    private String d7Prevention;

    /** D8 总结与关闭 */
    private String d8Summary;

    /** 打回原因（打回必填） */
    private String rejectReason;

    /** 发起人用户ID */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 审核人用户ID */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
