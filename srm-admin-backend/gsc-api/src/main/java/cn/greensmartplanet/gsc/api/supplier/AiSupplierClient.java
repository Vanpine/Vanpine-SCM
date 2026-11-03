package cn.greensmartplanet.gsc.api.supplier;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteCreateRequest;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteVO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierRiskDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSearchQuery;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSummaryDTO;
import cn.greensmartplanet.gsc.api.supplier.factory.AiSupplierClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 供应商域服务 Feign 客户端契约 — 专供 AI 工具调用的供应商查询
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-supplier 的 InternalAiSupplierController 实现本接口，URL/参数编译期强一致</li>
 *   <li>AI 域用它替代直查 enterprise_profile / due_diligence 表，供应商数据由供应商域统一出口</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>查询语义：不存在返回 data=null（HTTP 200）；列表无则空列表；HTTP 错误仅代表服务故障</li>
 *   <li>降级（FallbackFactory）：抛 BizException——AI 工具查询是喂给模型作答的数据底座，
 *       缺了它模型会基于空数据编造答案，故不静默通过</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_SUPPLIER_SERVICE,
        path = "/internal/ai/supplier",
        contextId = "aiSupplierClient",
        fallbackFactory = AiSupplierClientFallbackFactory.class)
public interface AiSupplierClient {

    /** 按关键词搜索供应商列表（无则空列表） */
    @PostMapping("/search")
    R<List<AiSupplierSummaryDTO>> searchSuppliers(@RequestBody AiSupplierSearchQuery query);

    /** 按租户ID查供应商详情（含资质 + 拼好的准入状态；不存在返回 data=null） */
    @GetMapping("/{tenantId}")
    R<AiSupplierDetailDTO> getSupplierDetail(@PathVariable("tenantId") Long tenantId);

    /** 落库供应商备注（AI 工具 create_supplier_note 二次确认后；写操作，供应域记 @AuditLog） */
    @PostMapping("/note")
    R<AiSupplierNoteVO> createSupplierNote(@RequestBody AiSupplierNoteCreateRequest request);

    /** 供应商风险评分（AI 工具 risk_assess_supplier；按租户ID三维评分，不存在返回 data=null） */
    @GetMapping("/risk/{tenantId}")
    R<AiSupplierRiskDTO> assessSupplierRisk(@PathVariable("tenantId") Long tenantId);
}
