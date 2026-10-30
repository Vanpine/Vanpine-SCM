package cn.greensmartplanet.gsc.framework.web;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;

/**
 * 全局异常处理器 — 整个项目的"安全网"
 * <p>
 * 任何地方抛出的异常，最终都会在这里被捕获，转成统一的 JSON 格式返回给前端。
 * 这样前端拿到的永远是 {"code": xxx, "message": "xxx"} 格式，不会看到丑陋的500错误页面。
 *
 * @author gsc
 */
@Slf4j
@RestControllerAdvice   // 拦截所有 @RestController 抛出的异常
public class GlobalExceptionHandler {

    /**
     * 处理业务异常 — 比如"租户不存在"、"密码错误"等我们主动抛出的异常
     * 直接返回业务层定义的错误码和提示信息
     * <p>
     * HTTP 状态按业务码映射：401→UNAUTHORIZED（触发前端刷新Token流程）、403→FORBIDDEN、其余→400。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e) {
        log.warn("业务异常: traceId={}, code={}, message={}", MDC.get("traceId"), e.getCode(), e.getMessage());
        HttpStatus httpStatus = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(httpStatus)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理权限不足 — 用户已登录但没这个接口的权限
     * 返回 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(403, "无权限访问"));
    }

    /**
     * 处理参数校验失败 — 比如 @Valid 校验不通过
     * 返回 400 + 第一个校验失败的字段的提示信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, msg));
    }

    /**
     * 处理用户名/邮箱等唯一键冲突 — 如 uk_tenant_username 重复
     * 返回 400 + 友好的中文提示，不暴露数据库内部信息
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<R<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("数据重复: traceId={}, message={}", MDC.get("traceId"), e.getMessage());
        String msg = "数据已存在，请勿重复操作";
        // 根据约束名区分提示
        String causeMsg = e.getCause() != null ? e.getCause().getMessage() : "";
        if (causeMsg.contains("uk_tenant_username")) {
            msg = "该用户名已被使用，请更换";
        } else if (causeMsg.contains("uk_tenant_email") || causeMsg.contains("uk_email")) {
            msg = "该邮箱已被注册";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, msg));
    }

    /**
     * 处理数据完整性异常 — 外键约束、非空约束等
     * 返回 400，不暴露数据库内部信息
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性异常: traceId={}, message={}", MDC.get("traceId"), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "数据操作失败，请检查输入信息"));
    }

    /**
     * 处理 IO 异常 — 文件上传失败等
     * 返回 500，消息来自 OssService 包装后的 IOException
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<R<Void>> handleIOException(IOException e) {
        log.error("IO异常: traceId={}, message={}", MDC.get("traceId"), e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, e.getMessage()));
    }

    /**
     * 兜底处理 — 所有上面没匹配到的异常都来这里
     * 返回 500，并打印完整堆栈方便排查问题
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        log.error("系统异常, traceId={}", MDC.get("traceId"), e); // 打印完整堆栈，方便排查
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务器内部错误"));
    }
}
