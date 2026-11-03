package cn.greensmartplanet.gsc.api.constant;

/**
 * 微服务服务名常量 — 所有 @FeignClient 统一引用，避免服务名字符串散落各处
 * <p>
 * 与各服务 spring.application.name 及网关 lb:// 路由一一对应；
 * 服务改名时只改这一处，编译期即可发现全部引用点。
 *
 * @author gsc
 */
public class ServiceNameConstants {

    /** 平台域服务（用户/角色/菜单/租户/入驻/通知/审计） */
    public static final String GSC_PLATFORM_SERVICE = "gsc-service-platform";

    /** 交易域服务（采购需求/询盘/报价/合同） */
    public static final String GSC_TRADE_SERVICE = "gsc-service-trade";

    /** 交付域服务（交付单/物流事件/风险/补救工单） */
    public static final String GSC_DELIVERY_SERVICE = "gsc-service-delivery";

    /** 供应商域服务（尽调/画像/AI匹配/对接） */
    public static final String GSC_SUPPLIER_SERVICE = "gsc-service-supplier";

    private ServiceNameConstants() {
    }
}
