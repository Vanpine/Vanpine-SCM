package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.supplier.dto.UpdateDdInfoDTO;
import cn.greensmartplanet.gsc.supplier.service.DueDiligenceService;
import cn.greensmartplanet.gsc.supplier.vo.DueDiligenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 尽调控制器（供应商域）— 尽调记录查/改
 * <p>
 * ⚠️ 微服务拆分端点归属：
 * <ul>
 *   <li><b>本服务</b>：dd-info（保存尽调信息）/ dd-detail（查看尽调资料）
 *       ——操作供应商域自有表 due_diligence</li>
 *   <li><b>报告 PDF 生成</b>：generate-report 已拆分到 {@link DdReportController}</li>
 *   <li><b>平台域</b>：pay / assign-firm / firm/* / confirm-dd / firms/search
 *       ——事务所流程与申请状态强耦合（user/role/mail/application 表），保留在 platform 的 DdFlowController</li>
 * </ul>
 * URL 保持不变，前端零感知。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/applications/{id}")
@RequiredArgsConstructor
@Tag(name = "尽调管理", description = "尽调操作（供应商域）")
public class DueDiligenceController {

    /** 尽调简单 CRUD Service（查/改） */
    private final DueDiligenceService dueDiligenceService;

    /**
     * 保存尽调扩展信息（用户在尽调页面填写省份/城市/资质文件等）
     * <p>
     * 接收专用 DTO 而非实体：实体含支付/事务所/报告等系统字段，
     * 直接开放给前端会被篡改；且实体字段名与前端提交名对不上时会被静默丢弃。
     * Service 层做校验 + 有则更新无则新增 + 多值附件子表整体替换。
     */
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "保存尽调信息", targetType = "DueDiligence")
    @Operation(summary = "保存尽调扩展信息")
    @PutMapping("/dd-info")
    public R<Void> updateDdInfo(@PathVariable Long id,
                                @RequestBody UpdateDdInfoDTO dto) {
        dueDiligenceService.saveOrUpdate(id, dto);
        return R.ok();
    }

    /**
     * 尽调查看资料（只查 due_diligence 表）
     * <p>
     * 内部调用 DueDiligenceService.getByApplicationId，会自动做重叠字段兜底。
     */
    @Operation(summary = "尽调查看资料")
    @GetMapping("/dd-detail")
    public R<DueDiligenceVO> ddDetail(@PathVariable Long id) {
        return R.ok(dueDiligenceService.getByApplicationId(id));
    }
}
