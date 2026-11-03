package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商搜索摘要契约 DTO — 供应商域内部接口返回给 AI 工具（search_suppliers）的字段子集
 * <p>
 * 供 AI 自然语言问答展示：公司名 / 行业 / 品类 / 地区 / 资质 / 准入状态。
 * 雪花主键出参由全局 Long→String 配置统一序列化为字符串（与 {@link ProfileInfoDTO} 一致），
 * 本 DTO 不额外注解。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSupplierSummaryDTO {

    /** 供应商租户ID（雪花主键，序列化为字符串） */
    private Long tenantId;

    /** 公司名称 */
    private String companyName;

    /** 所属行业 */
    private String industry;

    /** 生产品类（逗号分隔） */
    private String supCategories;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 资质认证（逗号分隔） */
    private String supCerts;

    /** 准入状态：approved / in_review / not_submitted（由供应商域拼好，AI 不推理） */
    private String accessStatus;
}
