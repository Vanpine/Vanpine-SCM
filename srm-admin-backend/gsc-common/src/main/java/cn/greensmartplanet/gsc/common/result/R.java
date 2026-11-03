package cn.greensmartplanet.gsc.common.result;

import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;

/**
 * 统一响应体 — 后端返回给前端的所有 JSON 都用这个格式
 * <p>
 * 返回示例：
 * <pre>{@code
 *   { "code": 200, "message": "操作成功", "data": {...}, "timestamp": 1720435200000 }
 * }</pre>
 * <p>
 * 用法：
 * <pre>{@code
 *   return R.ok(user);                        // 成功，带数据
 *   return R.ok();                            // 成功，无数据
 *   return R.fail(ErrorCode.TENANT_NOT_FOUND); // 失败，用枚举
 *   return R.fail(400, "用户名不能为空");        // 失败，自定义
 * }</pre>
 *
 * @author gsc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    /** 状态码，200 表示成功，其他表示失败 */
    private int code;
    /** 给前端看的提示信息 */
    private String message;
    /** 实际返回的数据 */
    private T data;
    /** 响应时间戳（毫秒） */
    private long timestamp;
    /**
     * 链路追踪 ID（仅失败时返回）
     * <p>
     * 取自日志上下文 MDC（Micrometer Tracing 自动写入）。用户报障时把此 ID 贴给开发，
     * 在 Zipkin 或日志里 grep 该 ID 即可定位整条调用链。成功响应不返回（null 序列化时省略）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String traceId;

    // ==================== 成功 ====================

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.message = "操作成功";
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    // ==================== 失败 ====================

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.data = null;
        r.timestamp = System.currentTimeMillis();
        // 从日志上下文取 traceId（链路追踪开启时 MDC 自动有值；未开启/异步线程无值时返回 null 省略）
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(500, message);
    }

    // ==================== 工具方法 ====================

    public boolean isSuccess() {
        return this.code == 200;
    }
}
