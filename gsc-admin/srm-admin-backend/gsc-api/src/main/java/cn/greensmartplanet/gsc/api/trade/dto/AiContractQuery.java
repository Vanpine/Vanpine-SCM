package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同查询入参契约 DTO — AI 工具 list_contracts 的请求体
 * <p>
 * tenantId 由调用方（AI 域）从登录态注入，禁止由模型直接传值（防越权，红线 2）。
 * page / pageSize 允许为空，提供方 Service 层兜底默认值并校验范围。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContractQuery {

    /** 当前租户ID（强制取登录态，不接受模型传入） */
    private Long tenantId;

    /** 状态筛选（可空） */
    private String status;

    /** 关键词（合同标题模糊匹配，可空） */
    private String keyword;

    /** 角色视角：buyer / supplier（空则默认 buyer） */
    private String role;

    /** 页码（从 1 开始，空则默认 1） */
    private Integer page;

    /** 每页条数（空则默认 10） */
    private Integer pageSize;
}
