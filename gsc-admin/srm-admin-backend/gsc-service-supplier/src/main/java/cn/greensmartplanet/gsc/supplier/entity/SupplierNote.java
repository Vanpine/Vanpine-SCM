package cn.greensmartplanet.gsc.supplier.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 供应商备注实体 — 采购商对供应商的私有标注（AI 助手二次确认后落库）
 * <p>
 * 归属语义：owner_tenant_id 为记备注的租户（租户隔离键，只有该租户能看到自己记的备注），
 * supplier_tenant_id 为被备注的供应商租户。区别于画像/尽调这类「供应商自身共享数据」，
 * 备注是采购商视角的私有评价，A 记的备注对 B 不可见。
 * <p>
 * 写入只经 AI 域二次确认接口 + 前端确认按钮，落库方法打 @AuditLog。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("supplier_note")
public class SupplierNote {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 记备注的租户（租户隔离键） */
    private Long ownerTenantId;

    /** 被备注的供应商租户 */
    private Long supplierTenantId;

    /** 备注内容 */
    private String content;

    /** 记备注的用户ID */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
