package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.supplier.service.DdReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 尽调报告 Controller（供应商域）— 生成尽调报告 PDF
 * <p>
 * 单资源控制器：只服务尽调报告 PDF 生成。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/applications/{id}")
@RequiredArgsConstructor
@Tag(name = "尽调报告", description = "尽调报告 PDF（供应商域）")
public class DdReportController {

    /** 尽调报告 PDF 生成 Service */
    private final DdReportPdfService ddReportPdfService;

    /**
     * 生成尽调报告 PDF
     * <p>
     * 按套餐类型+语言动态生成尽调报告 PDF，系统数据自动填入，事务所填写区留空。
     * 生成后自动上传 OSS，返回下载 URL。
     *
     * @param lang 语言：zh 中文（默认）/ en 英文
     */
    @AuditLog(module = "尽调管理", operation = "CREATE", desc = "生成尽调报告", targetType = "DueDiligence")
    @Operation(summary = "生成尽调报告 PDF")
    @PostMapping("/generate-report")
    public R<Map<String, String>> generateReport(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "zh") String lang) {
        return R.ok(ddReportPdfService.generate(id, lang));
    }
}
