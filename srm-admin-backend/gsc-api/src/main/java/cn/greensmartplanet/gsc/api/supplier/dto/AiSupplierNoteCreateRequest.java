package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商备注落库请求契约 DTO — AI 工具 create_supplier_note 二次确认后由 AI 域调用
 * <p>
 * 备注是「采购商对供应商的私有标注」：ownerTenantId 为记备注的租户（租户隔离键），
 * supplierTenantId 为被备注的供应商租户，二者由 AI 域从登录态/提案注入，模型不可伪造。
 * <p>
 * 安全边界：本 DTO 只在 AI 域前端「确认」接口（登录态 JWT 透传）里被组装并调用，
 * 供应域据此落库 + 记 @AuditLog，模型/中间服务无法绕过二次确认直接落库。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSupplierNoteCreateRequest {

    /** 记备注的租户（租户隔离键，AI 域从登录态注入） */
    private Long ownerTenantId;

    /** 被备注的供应商租户（来自二次确认提案） */
    private Long supplierTenantId;

    /** 备注内容（模型生成，经用户确认后落库） */
    private String content;

    /** 记备注的用户ID（AI 域从登录态注入，审计归属用） */
    private Long createBy;
}
