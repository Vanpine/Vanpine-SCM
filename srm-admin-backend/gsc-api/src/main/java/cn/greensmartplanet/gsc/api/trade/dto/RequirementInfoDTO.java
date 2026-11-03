package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 采购需求基础信息契约 DTO — 交易域内部接口返回给供应商域的需求字段子集
 * <p>
 * 供应商域使用场景（Feign 契约）：
 * <ul>
 *   <li>对接流程校验（存在/open/归属）与需求标题回填</li>
 *   <li>对接列表批量标题回填</li>
 * </ul>
 * 契约约定：需求不存在返回 data=null（HTTP 200），调用方按 null 处理业务分支。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementInfoDTO {

    /** 需求ID */
    private Long id;
    /** 发布方租户ID */
    private Long tenantId;
    /** 需求标题 */
    private String title;
    /** 状态：open/completed/closed/expired */
    private String status;
    /** 目标供应商数（对接达标自动 completed 用） */
    private Integer targetSupplierCount;
}
