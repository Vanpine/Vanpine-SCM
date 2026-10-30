package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传控制器
 * 头像、附件等统一上传到 OSS
 * 异常由 GlobalExceptionHandler 统一处理，Controller 不 try-catch
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "文件上传", description = "头像、附件等上传到 OSS")
public class FileController {

    private final OssService ossService;  // OSS 上传服务

    /** 允许上传的存储目录白名单（防前端传 ../../ 之类越权写入桶内任意路径） */
    private static final Set<String> ALLOWED_DIRS = Set.of(
            "avatar", "inquiry", "quote", "contract", "delivery", "dd", "ai-doc", "dd-report");

    /** 上传文件到 OSS，返回可直接访问的短效预签名 URL */
    @AuditLog(module = "文件管理", operation = "CREATE", desc = "上传文件")
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                          @RequestParam(defaultValue = "avatar") String dir) throws IOException {
        // 目录白名单校验：dir 由前端传入且直接拼进 OSS key，不校验可写入桶内任意前缀
        if (!ALLOWED_DIRS.contains(dir)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的上传目录");
        }
        // upload() 返回 object key，这里现签一个短效 URL 给前端即时使用（不再返回 30 天长期签名）
        String key = ossService.upload(file, dir);
        String url = ossService.generateGetUrl(key);
        return R.ok(Map.of("url", url, "key", key, "originalName", file.getOriginalFilename()));
    }
}
