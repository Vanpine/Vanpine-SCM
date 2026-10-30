package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.service.AiDocumentService;
import cn.greensmartplanet.gsc.ai.vo.DocumentVO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * AI 知识库文档控制器
 * <p>
 * 上传 / 列表 / 删除。写操作打 @AuditLog；异常由 GlobalExceptionHandler 统一处理。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/ai/documents")
@RequiredArgsConstructor
@Tag(name = "AI 对话机器人", description = "会话管理与知识库问答")
public class AiDocumentController {

    private final AiDocumentService documentService;

    /** 上传知识库文档（multipart，txt/md/pdf/docx/xlsx ≤10MB） */
    @Operation(summary = "上传知识库文档")
    @AuditLog(module = "AI 对话机器人", operation = "CREATE", desc = "上传知识库文档", targetType = "AiDocument")
    @PostMapping
    public R<DocumentVO> upload(@RequestParam("file") MultipartFile file,
                                @AuthenticationPrincipal JwtUserDetails user) throws IOException {
        return R.ok(documentService.upload(file, user.getTenantId(), user.getUserId()));
    }

    /** 查当前租户文档列表 */
    @Operation(summary = "知识库文档列表")
    @GetMapping
    public R<List<DocumentVO>> list(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(documentService.listDocuments(user.getTenantId()));
    }

    /** 删除文档（逻辑删文档 + 物理删分块 + 删向量） */
    @Operation(summary = "删除知识库文档")
    @AuditLog(module = "AI 对话机器人", operation = "DELETE", desc = "删除知识库文档", targetType = "AiDocument")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @AuthenticationPrincipal JwtUserDetails user) {
        documentService.deleteDocument(id, user.getTenantId());
        return R.ok();
    }

    /** 重新解析失败的文档（清旧向量 → 重跑解析 → 回写状态） */
    @Operation(summary = "重新解析知识库文档")
    @AuditLog(module = "AI 对话机器人", operation = "UPDATE", desc = "重新解析知识库文档", targetType = "AiDocument")
    @PostMapping("/{id}/reparse")
    public R<DocumentVO> reparse(@PathVariable Long id, @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(documentService.reparse(id, user.getTenantId()));
    }

    /** 生成文档预览地址（校验租户归属后按 oss_key 现签短效 URL，前端直接用于 iframe 打开） */
    @Operation(summary = "预览知识库文档")
    @GetMapping("/{id}/preview")
    public R<String> preview(@PathVariable Long id, @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(documentService.previewUrl(id, user.getTenantId()));
    }
}
