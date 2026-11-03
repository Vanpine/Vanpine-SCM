package cn.greensmartplanet.gsc.framework.audit;

import java.lang.annotation.*;

/**
 * 操作审计日志注解 — 打在 Controller 方法上，自动记录谁在什么时候做了什么操作
 * <p>
 * 用法：
 * <pre>{@code
 *   @AuditLog(module = "租户管理", operation = "UPDATE", desc = "审核通过并开通租户")
 *   @PostMapping("/review")
 *   public R<Void> review(...) { ... }
 * }</pre>
 * <p>
 * 打了这个注解的方法，会被 AOP 切面自动拦截，记录到 audit_log 表中。
 *
 * @author gsc
 */
@Documented
@Target(ElementType.METHOD)      // 只能打在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时有效
public @interface AuditLog {

    /** 操作模块，比如 "租户管理"、"用户管理" */
    String module();

    /** 操作类型，比如 "CREATE"、"UPDATE"、"DELETE"、"LOGIN" */
    String operation();

    /** 操作描述，比如 "审核通过租户【某某公司】" */
    String desc() default "";

    /** 操作目标类型（如 Inquiry/Quote/Contract），用于日志快速定位 */
    String targetType() default "";
}
