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
 * 交付承诺日期实体（append-only）
 * <p>
 * 交期延误时供应商主动提交新承诺交期，采购方确认/拒绝。
 * 每次提交追加一条记录（历史留痕），确认后写回 delivery.confirmed_delivery_date
 * 作为考核基准（OTD/预警/风险扫描 COALESCE 回退合同交期）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_promise_date")
public class DeliveryPromiseDate {

    /** 主键（雪花ID：ASSIGN_ID 注解 + 自定义 XML insert 显式传 id，id 由 Service 层 IdWorker 生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联交付单ID */
    private Long deliveryId;

    /** 承诺交期 */
    private LocalDate promisedDate;

    /** 原因（必填） */
    private String reason;

    /** 状态：pending 待确认 / accepted 已确认 / rejected 已拒绝 */
    private String status;

    /** 确认/拒绝人用户ID */
    private Long confirmUserId;

    /** 确认/拒绝时间 */
    private LocalDateTime confirmTime;

    /** 提交人用户ID */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
