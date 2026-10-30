package cn.greensmartplanet.gsc.common.exception;

import lombok.Getter;

/**
 * 业务异常类 — 用来"主动抛出去"告诉前端出了什么问题
 * <p>
 * 用法举例：
 * <pre>{@code
 *   if (tenant == null) {
 *       throw new BizException(ErrorCode.TENANT_NOT_FOUND);
 *   }
 * }</pre>
 * 然后 GlobalExceptionHandler 会自动抓到它，转成 JSON 返回给前端
 *
 * @author gsc
 */
@Getter
public class BizException extends RuntimeException {

    /** HTTP 错误码，比如 404、401、403 等 */
    private final int code;

    /**
     * 构造器1：直接传错误码数字和提示文字
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造器2：传 ErrorCode 枚举（推荐用法）
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 构造器3：传 ErrorCode 枚举但覆盖提示文字（特殊场景用）
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
