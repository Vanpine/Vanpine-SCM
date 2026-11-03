package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteCreateRequest;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteVO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierRiskDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSearchQuery;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSummaryDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.supplier.service.ProfileQueryService;
import cn.greensmartplanet.gsc.supplier.service.SupplierNoteService;
import cn.greensmartplanet.gsc.supplier.service.SupplierRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商内部接口 — AI 工具供应商查询（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 AiSupplierClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/ai/supplier")
@RequiredArgsConstructor
public class InternalAiSupplierController {

    private final ProfileQueryService profileQueryService;
    private final SupplierNoteService supplierNoteService;
    private final SupplierRiskService supplierRiskService;

    /** 按关键词搜索供应商列表（AI 工具 search_suppliers；无则空列表） */
    @PostMapping("/search")
    public R<List<AiSupplierSummaryDTO>> searchSuppliers(@RequestBody AiSupplierSearchQuery query) {
        int page = query.getPage() == null ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        return R.ok(profileQueryService.searchForAi(query.getKeyword(), page, pageSize));
    }

    /** 按租户ID查供应商详情（AI 工具 get_supplier_detail；不存在返回 data=null） */
    @GetMapping("/{tenantId}")
    public R<AiSupplierDetailDTO> getSupplierDetail(@PathVariable("tenantId") Long tenantId) {
        return R.ok(profileQueryService.getDetailForAi(tenantId));
    }

    /**
     * 落库供应商备注（AI 工具 create_supplier_note 二次确认后调用）。
     * <p>
     * ⚠️ 本接口是 Feign 中转（SecurityContext 无登录态），审计不在本处记——
     * 真正的操作者在 AI 域「确认」接口，由该接口记 @AuditLog（targetType=SupplierNote）。
     * 本接口只做数据落库，ownerTenantId/createBy 由 AI 域从登录态注入。
     */
    @PostMapping("/note")
    public R<AiSupplierNoteVO> createSupplierNote(@RequestBody AiSupplierNoteCreateRequest request) {
        return R.ok(supplierNoteService.createForAi(request));
    }

    /** 供应商风险评分（AI 工具 risk_assess_supplier；不存在返回 data=null） */
    @GetMapping("/risk/{tenantId}")
    public R<AiSupplierRiskDTO> assessSupplierRisk(@PathVariable("tenantId") Long tenantId) {
        return R.ok(supplierRiskService.assess(tenantId));
    }
}
