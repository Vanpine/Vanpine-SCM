package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商搜索查询入参契约 DTO — AI 工具 search_suppliers 的请求体
 * <p>
 * page / pageSize 允许为空，提供方 Service 层兜底默认值（1 / 10）并校验范围。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSupplierSearchQuery {

    /** 关键词（企业名 / 行业 / 地区 / 品类模糊匹配，可空） */
    private String keyword;

    /** 页码（从 1 开始，空则默认 1） */
    private Integer page;

    /** 每页条数（空则默认 10） */
    private Integer pageSize;
}
