package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteCreateRequest;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteVO;

/**
 * 供应商备注服务接口 — AI 助手二次确认后落库（写操作）
 * <p>
 * 归属校验：ownerTenantId 为记备注租户，落库前校验 supplierTenantId 对应的供应商画像存在。
 *
 * @author gsc
 */
public interface SupplierNoteService {

    /**
     * 新增供应商备注（AI 工具 create_supplier_note 二次确认后调用）。
     *
     * @param request 备注落库请求（ownerTenantId/supplierTenantId/content/createBy）
     * @return 落库成功后的备注回执
     */
    AiSupplierNoteVO createForAi(AiSupplierNoteCreateRequest request);
}
