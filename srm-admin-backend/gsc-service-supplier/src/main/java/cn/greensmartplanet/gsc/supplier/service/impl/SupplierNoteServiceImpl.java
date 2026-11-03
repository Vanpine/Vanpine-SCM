package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteCreateRequest;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteVO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.entity.SupplierNote;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.mapper.SupplierNoteMapper;
import cn.greensmartplanet.gsc.supplier.service.SupplierNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 供应商备注服务实现 — AI 助手二次确认后落库（写操作）
 * <p>
 * 防御：落库前校验被备注的供应商画像存在，避免备注悬挂在无效供应商上；
 * ownerTenantId/supplierTenantId 由 AI 域从登录态与提案注入，模型不可伪造。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierNoteServiceImpl implements SupplierNoteService {

    private final SupplierNoteMapper supplierNoteMapper;
    private final EnterpriseProfileMapper enterpriseProfileMapper;

    /**
     * 新增供应商备注：校验供应商画像存在 → 组装实体落库 → 回执 VO。
     */
    @Override
    public AiSupplierNoteVO createForAi(AiSupplierNoteCreateRequest request) {
        // 1. 防御：被备注的供应商画像必须存在（备注不能悬挂在无效租户上）
        EnterpriseProfile supplier = enterpriseProfileMapper.selectByTenantId(request.getSupplierTenantId());
        if (supplier == null) {
            throw new BizException(ErrorCode.MATCH_PROFILE_NOT_FOUND);
        }
        // 2. 组装实体（ownerTenantId 为租户隔离键，createBy 为审计归属）
        SupplierNote note = SupplierNote.builder()
                .ownerTenantId(request.getOwnerTenantId())
                .supplierTenantId(request.getSupplierTenantId())
                .content(request.getContent())
                .createBy(request.getCreateBy())
                .build();
        supplierNoteMapper.insert(note);
        log.info("AI 工具供应商备注已落库: owner={}, supplier={}, id={}",
                request.getOwnerTenantId(), request.getSupplierTenantId(), note.getId());
        // 3. 回执 VO（雪花 id 出参序列化为字符串）
        return AiSupplierNoteVO.builder()
                .id(note.getId())
                .supplierTenantId(note.getSupplierTenantId())
                .content(note.getContent())
                .createTime(note.getCreateTime())
                .build();
    }
}
