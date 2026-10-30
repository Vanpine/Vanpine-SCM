package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 供应商备注契约 VO — create_supplier_note 落库成功后的回执
 * <p>
 * 只带前端/AI 需要的字段子集，雪花主键出参由全局 Long→String 配置统一序列化为字符串。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSupplierNoteVO {

    /** 备注主键（雪花，序列化为字符串） */
    private Long id;

    /** 被备注的供应商租户 */
    private Long supplierTenantId;

    /** 备注内容 */
    private String content;

    /** 记录时间 */
    private LocalDateTime createTime;
}
