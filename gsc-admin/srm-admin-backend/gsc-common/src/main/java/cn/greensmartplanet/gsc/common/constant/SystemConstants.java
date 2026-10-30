package cn.greensmartplanet.gsc.common.constant;

/**
 * 系统常量类 — 存放项目中所有不会变的"固定值"
 * 比如：平台超管的ID是0、JWT过期时间是2小时、密码最多输错5次 等等
 *
 * @author gsc
 */
public final class SystemConstants {

    /**
     * 平台租户的 id，对应 tenant 表 id=1（种子数据手动指定）
     */
    public static final Long PLATFORM_TENANT_ID = 1L;
    /**
     * 超级管理员角色编码 — 只有平台超管才有这个角色
     */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    /**
     * 超级管理员通配权限码 — 拥有它即拥有全部权限（前端守卫据此放行平台超管）
     */
    public static final String PERM_SUPER_ADMIN_WILDCARD = "*:*:*";
    /**
     * 企业管理员角色编码 — 每个企业租户的第一个用户就是这个角色
     */
    public static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    /**
     * 尽调预审角色 — 入驻后尚未尽调通过，只能进尽调页
     */
    public static final String ROLE_DUE_DILIGENCE = "DUE_DILIGENCE";
    /**
     * 事务所角色 — 只能进事务所工作台
     */
    public static final String ROLE_FIRM = "FIRM";
    /**
     * JWT Token 内部存放租户ID时用的 key 名字
     */
    public static final String JWT_CLAIM_TENANT_ID = "tenant_id";
    /**
     * JWT Token 内部存放租户编码时用的 key 名字
     */
    public static final String JWT_CLAIM_TENANT_CODE = "tenant_code";
    /**
     * HTTP 请求头里放租户ID的 header 名称
     */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    /**
     * HTTP 请求头里放租户编码的 header 名称（网关注入，与租户ID配套）
     */
    public static final String HEADER_TENANT_CODE = "X-Tenant-Code";
    /**
     * HTTP 请求头里放内部共享密钥的名称 — 网关与 Feign 注入，下游校验通过才信任租户头
     */
    public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    /**
     * 登录Token有效期：7200秒 = 2小时
     */
    public static final long JWT_EXPIRATION = 7200L;
    /**
     * 刷新Token有效期：604800秒 = 7天，用户7天内不用重新登录
     */
    public static final long JWT_REFRESH_EXPIRATION = 604800L;
    /**
     * 密码最多输错几次就锁定
     */
    public static final int MAX_LOGIN_RETRY = 5;
    /**
     * 输错密码后锁定多久（秒）：900秒 = 15分钟
     */
    public static final long LOGIN_LOCK_SECONDS = 900L;


    /*
     *  登录页面 用户密码试错次数记录的key
     * */
    public static final String RETRY_KEY = "retry:key:";

    /** 密码重置验证码 Redis key 前缀 */
    public static final String PWD_RESET_CODE_PREFIX = "pwdReset:code:";
    /** 密码重置发送频率限制 Redis key 前缀 */
    public static final String PWD_RESET_SEND_PREFIX = "pwdReset:send:";

    /** 用户权限缓存 Redis key 前缀（perms:{userId}），TTL = JWT 过期时间 */
    public static final String PERMS_CACHE_PREFIX = "perms:";
    /** 用户权限版本号 Redis key 前缀（permsVersion:{userId}），权限变更时 +1 */
    public static final String PERMS_VERSION_PREFIX = "permsVersion:";
    /** accessToken 白名单 Redis key 前缀（accessToken:{userId}） */
    public static final String ACCESS_TOKEN_PREFIX = "accessToken:";
    /** refreshToken 白名单 Redis key 前缀（refreshToken:{userId}） */
    public static final String REFRESH_TOKEN_PREFIX = "refreshToken:";
    /** 强制下线原因 Redis key 前缀（kickReason:{userId}），只提示一次 */
    public static final String KICK_REASON_PREFIX = "kickReason:";
    /** 用户菜单树缓存 Redis key 前缀（menuTree:{userId}），TTL = JWT 过期时间 */
    public static final String MENU_TREE_CACHE_PREFIX = "menuTree:";
    /** 菜单全局版本号 key（menuVersion），菜单增删改时 +1，使所有用户菜单缓存失效 */
    public static final String MENU_VERSION_KEY = "menuVersion";
    /**
     * 智能匹配推荐结果 Redis key 前缀（match:recommend:{requirementId}:{fieldHash}）
     * <p>
     * 匹配会扫最多 500 家画像，AI 引擎还要调 Python；广场重复点「智能推荐」走缓存，
     * fieldHash 随品类/地域/资质/描述变化，需求改了自然 miss，不必跨域主动失效。
     */
    public static final String MATCH_RECOMMEND_CACHE_PREFIX = "match:recommend:";
    /** 找需求推荐缓存：match:plaza-req:{tenantId}:{profileHash} */
    public static final String MATCH_PLAZA_REQ_CACHE_PREFIX = "match:plaza-req:";
    /**
     * 天气缓存 Redis key 前缀（weather:geo:{地名} / weather:now:{lat,lon} / weather:fc:{days}:{lat,lon}）
     * <p>
     * 交付多实例共享，避免每台都打 Open-Meteo；天气无租户隔离。
     */
    public static final String WEATHER_CACHE_PREFIX = "weather:";
    /** 天气缓存默认 TTL（秒），与 gsc.weather.cache-minutes 默认 10 分钟对齐 */
    public static final long WEATHER_CACHE_TTL = 600L;
    /** 天气 AI 建议 Redis key：weather:advice:{riskId} */
    public static final String WEATHER_ADVICE_CACHE_PREFIX = "weather:advice:";
    /** 天气 AI 建议缓存 TTL（秒）：6 小时，同一风险不反复打模型 */
    public static final long WEATHER_ADVICE_CACHE_TTL = 21600L;
    /** 天气 AI 建议生成锁 TTL（秒） */
    public static final long WEATHER_ADVICE_LOCK_TTL = 90L;
    /** 匹配推荐缓存 TTL（秒）：10 分钟，对接状态不进缓存、每次现查 */
    public static final long MATCH_RECOMMEND_CACHE_TTL = 600L;
    /** 详情页 AI 理由生成锁 TTL（秒），避免并发重复打 DeepSeek */
    public static final long MATCH_REASON_LOCK_TTL = 90L;
    /**
     * 画像向量对账锁 TTL（秒）：30 分钟。多实例只有一个能跑本轮对账；
     * 全量同步上限 500 条 × 每条一次 HTTP，实测分钟级，30 分钟留足余量防锁提前过期。
     */
    public static final long PROFILE_RECONCILE_LOCK_TTL = 1800L;
    /**
     * 制裁名单同步锁 TTL（秒）：30 分钟。多实例只有一个能跑同步；
     * 单次同步含下载约 70MB CSV + 解析 + 入库（约 1-3 分钟），30 分钟留足余量防锁提前过期。
     */
    public static final long SANCTION_SYNC_LOCK_TTL = 1800L;
    /**
     * 交付风险扫描锁 TTL（秒）：4 分钟。任务每 5 分钟触发一次，TTL 必须小于周期，
     * 保证锁不会跨轮次残留；扫描本身是分钟级以内，4 分钟足够覆盖单次执行。
     */
    public static final long DELIVERY_RISK_SCAN_LOCK_TTL = 240L;
    /**
     * AI 会话生成锁 TTL（秒）：3 分钟。同一会话同时只允许一个生成中，
     * 取值需略大于「streaming 占位视为残留」的阈值（AiChatServiceImpl.STALE_STREAMING_SECONDS=150s），
     * 避免正常生成还没结束锁就过期、第二个请求趁虚而入。
     */
    public static final long AI_CHAT_GEN_LOCK_TTL = 180L;
    /**
     * AI 会话压缩锁 TTL（秒）：2 分钟。同一会话同时只允许一个实例做压缩——
     * CAS 只保证结果不互相覆盖，但多个实例会各自取同一批消息、各调一次大模型，纯属浪费。
     * 取值需大于「压缩调用本身的最长耗时」（Java 读超时 60s），留一倍余量。
     */
    public static final long AI_COMPACT_LOCK_TTL = 120L;
    /** 广场智能推荐单页上限，与前端 PAGE_SIZE_OPTIONS 对齐 */
    public static final int MATCH_RECOMMEND_MAX_PAGE_SIZE = 100;
    /**
     * 非超管不可分配的权限码 — 防止租户管理员自行提权
     * <p>
     * 通配码（*:*:*、admin）直接提权；tenant/menu 为平台专属功能（对应接口已限超管）。
     * 其余权限码（user/role/application/match 等）租户管理员可自由分配。
     */
    public static final java.util.Set<String> PLATFORM_ONLY_PERM_CODES =
            java.util.Set.of(PERM_SUPER_ADMIN_WILDCARD, "admin", "tenant", "menu");

    /**
     * 私有构造器，阻止外部 new 这个类（纯常量类不需要实例化）
     */
    private SystemConstants() {
    }
}
