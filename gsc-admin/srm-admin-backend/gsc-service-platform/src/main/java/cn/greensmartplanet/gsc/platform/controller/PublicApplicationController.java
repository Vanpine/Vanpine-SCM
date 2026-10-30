package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.platform.dto.SubmitApplicationDTO;
import cn.greensmartplanet.gsc.platform.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 入驻申请公开接口 — 官网"申请加入"调用，无需登录
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "入驻申请", description = "官网入驻申请提交（公开接口）")
public class PublicApplicationController {

    private final ApplicationService applicationService;

    /** 提交入驻申请（官网"申请加入"表单） */
    @Operation(summary = "提交入驻申请")
    @AuditLog(module = "入驻管理", operation = "CREATE", desc = "提交入驻申请", targetType = "Application")
    @PostMapping("/applications")
    public R<Map<String, Object>> submit(@Valid @RequestBody SubmitApplicationDTO dto) {
        return R.ok(applicationService.submit(dto));
    }
}
