package cn.greensmartplanet.gsc.framework.tenant;

/**
 * 租户上下文持有者 — 用 ThreadLocal 技术在当前请求的"线程"里暂存租户信息
 * <p>
 * 核心概念：
 *   ThreadLocal 就像一个"线程专属小盒子"，每个请求进来都是一个独立线程，
 *   把租户ID放进这个盒子里，整个请求处理过程中任何地方都能拿出来用，
 *   请求结束后清空盒子，不会影响下一个请求。
 * <p>
 * 用法：
 *   请求进来 → TenantContextHolder.setTenantId(100L);  // 把"租户ID=100"放进盒子
 *   处理业务 → TenantContextHolder.getTenantId();       // 任何地方取出来 → 100
 *   请求结束 → TenantContextHolder.clear();              // 清空盒子，防止内存泄漏
 *
 * @author gsc
 */
public class TenantContextHolder {

    /** 存放当前租户ID的 ThreadLocal 盒子 */
    private static final ThreadLocal<Long> TENANT_CONTEXT = new ThreadLocal<>();

    /** 存放当前租户编码的 ThreadLocal 盒子 */
    private static final ThreadLocal<String> TENANT_CODE_CONTEXT = new ThreadLocal<>();

    /** 把租户ID放进当前线程的盒子 */
    public static void setTenantId(Long tenantId) {
        TENANT_CONTEXT.set(tenantId);
    }

    /** 从当前线程的盒子取出租户ID */
    public static Long getTenantId() {
        return TENANT_CONTEXT.get();
    }

    /** 把租户编码放进当前线程的盒子 */
    public static void setTenantCode(String tenantCode) {
        TENANT_CODE_CONTEXT.set(tenantCode);
    }

    /** 从当前线程的盒子取出租户编码 */
    public static String getTenantCode() {
        return TENANT_CODE_CONTEXT.get();
    }

    /**
     * 清空当前线程的盒子（必须！）
     * 每次请求结束后调用，防止：
     *   1. 内存泄漏（线程复用场景下ThreadLocal不清除会越积越多）
     *   2. 数据串号（下一个请求复用线程时拿到上一个请求的租户ID）
     */
    public static void clear() {
        TENANT_CONTEXT.remove();
        TENANT_CODE_CONTEXT.remove();
    }
}
