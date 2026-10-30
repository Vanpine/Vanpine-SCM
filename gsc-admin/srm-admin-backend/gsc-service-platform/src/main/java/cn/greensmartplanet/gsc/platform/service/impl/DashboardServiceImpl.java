package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.api.delivery.DeliveryStatsClient;
import cn.greensmartplanet.gsc.api.delivery.dto.DeliveryStatsDTO;
import cn.greensmartplanet.gsc.api.trade.TradeStatsClient;
import cn.greensmartplanet.gsc.api.trade.dto.TradeStatsDTO;
import cn.greensmartplanet.gsc.platform.entity.Notification;
import cn.greensmartplanet.gsc.platform.mapper.DashboardStatsMapper;
import cn.greensmartplanet.gsc.platform.mapper.NotificationMapper;
import cn.greensmartplanet.gsc.platform.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台统计实现 — 待办驱动（阶段 8.1 Dashboard BFF）
 * <p>
 * 与原单体 DashboardServiceImpl 逐项一致：
 * <ul>
 *   <li>平台域本域：租户计数（DashboardStatsMapper）、近期动态（notification 表）</li>
 *   <li>询盘/合同计数：TradeStatsClient Feign 聚合（SQL 随表迁 trade，口径不变）</li>
 *   <li>交付/超期计数：DeliveryStatsClient Feign 聚合（SQL 随表迁 delivery，口径不变）</li>
 *   <li>统计数字为展示性数据，Feign 降级返回 0 时工作台照常渲染</li>
 * </ul>
 *
 * @author gsc
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final List<String> TENANT_ACTIVE = List.of("active", "pending", "suspended");
    private static final List<String> TENANT_PENDING = List.of("pending");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DashboardStatsMapper dashboardStatsMapper;
    private final NotificationMapper notificationMapper;
    // 询盘/合同计数归交易域，交付/超期计数归交付域，Feign 聚合（降级 0 不阻断工作台）
    private final TradeStatsClient tradeStatsClient;
    private final DeliveryStatsClient deliveryStatsClient;

    /** 工作台聚合：按角色分发 stats/todos/近期动态，双身份合并采购+供应两组指标 */
    @Override
    public Map<String, Object> getDashboard(Long tenantId, String role) {
        // 1. 面板容器：stats 指标卡 + todos 待办项，按角色填充
        List<Map<String, Object>> stats = new ArrayList<>();
        List<Map<String, Object>> todos = new ArrayList<>();

        // 2. 角色分发：双身份合并两组指标，其余角色各取对应视角
        switch (role == null ? "" : role) {
            case "SUPER_ADMIN" -> buildSuperAdmin(stats, todos); // 超管：本地租户计数 + Feign 平台合同总数
            case "BUYER", "SUPPLIER", "BOTH" -> {
                // 2a. Feign 聚合两域统计（降级 0，工作台照常渲染）
                TradeStatsDTO trade = tradeStatsClient.getDashboardStats(tenantId).getData();
                DeliveryStatsDTO delivery = deliveryStatsClient.getDashboardStats(tenantId).getData();
                if ("BUYER".equals(role)) { // 采购视角：只取采购指标
                    stats.addAll(buyerStats(trade, delivery));
                    todos.addAll(buyerTodos(trade, delivery, ""));
                } else if ("SUPPLIER".equals(role)) { // 供应视角：只取供应指标
                    stats.addAll(supplierStats(trade, delivery));
                    todos.addAll(supplierTodos(trade, delivery, ""));
                } else { // 双身份：采购 + 供应完整指标都展示，待办合并用标签区分
                    stats.addAll(buyerStats(trade, delivery));
                    stats.addAll(supplierStats(trade, delivery));
                    todos.addAll(buyerTodos(trade, delivery, "采购·"));
                    todos.addAll(supplierTodos(trade, delivery, "供应·"));
                }
            }
            default -> { /* TENANT_ADMIN / 其他：空面板，前端静态 quickActions 兜底 */ }
        }

        // 3. 组装响应：stats + todos + 近期动态（复用通知表）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("todos", todos);
        result.put("recentActivities", buildRecentActivities(tenantId, role));
        return result;
    }

    // ==================== 超管 ====================

    /** 超管面板：租户总数、待审核入驻、平台合同总数 */
    private void buildSuperAdmin(List<Map<String, Object>> stats, List<Map<String, Object>> todos) {
        // 1. 平台合同总数归交易域：Feign 聚合（tenantId=null 只取平台级总数，降级 0）
        long contractAll = tradeStatsClient.getDashboardStats(null).getData().getContractAll();

        // 2. 指标卡：租户总数/今日活跃（无埋点占位 0）/待审核入驻/平台合同总数
        stats.add(stat("租户总数", dashboardStatsMapper.countTenantByStatuses(TENANT_ACTIVE), "个", "/admin/tenant", null));
        stats.add(stat("今日活跃租户", 0, "个", "/admin/tenant", null));
        stats.add(stat("待审核入驻", dashboardStatsMapper.countTenantByStatuses(TENANT_PENDING), "个", "/admin/application", null));
        stats.add(stat("平台合同总数", contractAll, "份", null, null));

        // 3. 待办：入驻申请待审核（高危）
        todos.add(todo("入驻申请待审核", dashboardStatsMapper.countTenantByStatuses(TENANT_PENDING), "/admin/application", "high"));
    }

    // ==================== 采购方 ====================

    /** 采购方指标：进行中询盘/待比价/进行中合同/待验收交付 */
    private List<Map<String, Object>> buyerStats(TradeStatsDTO trade, DeliveryStatsDTO delivery) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(stat("进行中询盘", trade.getInquiryActive(), "单", "/procurement/inquiry", "采购"));
        list.add(stat("待比价询盘", trade.getInquiryQuoting(), "单", "/procurement/compare", "采购"));
        list.add(stat("进行中合同", trade.getContractActiveBuyer(), "份", "/procurement/contract", "采购"));
        list.add(stat("待验收交付", delivery.getDeliveryBuyerToAccept(), "笔", "/delivery/manage/acceptance", "采购"));
        return list;
    }

    /** 采购方待办：待比价、合同待确认/待盖章、交付待收货/待验收、超期项 */
    private List<Map<String, Object>> buyerTodos(TradeStatsDTO trade, DeliveryStatsDTO delivery, String prefix) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(todo(prefix + "询盘待比价", trade.getInquiryQuoting(), "/procurement/compare", "high"));
        list.add(todo(prefix + "合同待供应商确认", trade.getContractPendingSupplierBuyer(), "/procurement/contract", "medium"));
        list.add(todo(prefix + "合同待盖章", trade.getContractPendingConfirmBuyer(), "/procurement/contract", "medium"));
        list.add(todo(prefix + "交付待收货", delivery.getDeliveryBuyerToReceive(), "/delivery/manage/receipt", "medium"));
        list.add(todo(prefix + "交付待验收", delivery.getDeliveryBuyerToAccept(), "/delivery/manage/acceptance", "medium"));
        if (delivery.getOverdueUnreceived() > 0) {
            list.add(todo(prefix + "超期未收货", delivery.getOverdueUnreceived(), "/delivery/manage/receipt", "high"));
        }
        if (delivery.getOverdueUnaccepted() > 0) {
            list.add(todo(prefix + "超期未验收", delivery.getOverdueUnaccepted(), "/delivery/manage/acceptance", "high"));
        }
        return list;
    }

    // ==================== 供应方 ====================

    /** 供应方指标：待报价、已中标、进行中合同、待发货 */
    private List<Map<String, Object>> supplierStats(TradeStatsDTO trade, DeliveryStatsDTO delivery) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(stat("待报价询盘", trade.getPendingQuote(), "单", "/procurement/inquiry", "供应"));
        list.add(stat("已中标", trade.getAwardBySupplier(), "单", "/procurement/inquiry", "供应"));
        list.add(stat("进行中合同", trade.getContractActiveSupplier(), "份", "/procurement/contract", "供应"));
        list.add(stat("待发货", delivery.getDeliverySupplierToShip(), "笔", "/delivery/manage/shipment", "供应"));
        return list;
    }

    /** 供应方待办：待报价、合同待确认/待上传盖章件、交付待发货、超期项 */
    private List<Map<String, Object>> supplierTodos(TradeStatsDTO trade, DeliveryStatsDTO delivery, String prefix) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(todo(prefix + "询盘待报价", trade.getPendingQuote(), "/procurement/inquiry", "high"));
        list.add(todo(prefix + "合同待确认条款", trade.getContractPendingSupplierSupplier(), "/procurement/contract", "medium"));
        list.add(todo(prefix + "合同待上传盖章件", trade.getContractPendingConfirmSupplier(), "/procurement/contract", "medium"));
        list.add(todo(prefix + "交付待发货", delivery.getDeliverySupplierToShip(), "/delivery/manage/shipment", "medium"));
        if (delivery.getOverdueUnshipped() > 0) {
            list.add(todo(prefix + "超期未发货", delivery.getOverdueUnshipped(), "/delivery/manage/shipment", "high"));
        }
        return list;
    }

    // ==================== 近期动态（复用通知表） ====================

    /** 近期动态：复用通知表取最近 10 条，按类型映射跳转链接 */
    private List<Map<String, Object>> buildRecentActivities(Long tenantId, String role) {
        if ("SUPER_ADMIN".equals(role) || tenantId == null) {
            return Collections.emptyList(); // 超管动态占位，后续可接租户最近入驻
        }
        List<Notification> list = notificationMapper.selectPage(tenantId, null, 0, 10);
        return list.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", n.getRelatedType());
            m.put("desc", n.getContent());
            m.put("time", n.getCreateTime() != null ? n.getCreateTime().format(FMT) : "");
            m.put("link", linkByType(n.getRelatedType()));
            return m;
        }).toList();
    }

    /** 通知类型 → 前端跳转路径 */
    private String linkByType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "connection" -> "/matching/connections";
            case "contract" -> "/procurement/contract";
            case "quote" -> "/procurement/compare";
            case "inquiry" -> "/procurement/inquiry";
            default -> null;
        };
    }

    // ==================== 组装工具 ====================

    /**
     * @param group 双身份分组名（采购/供应）；单角色传 null，前端不展示分组标题
     * @param link  指标卡跳转路径，无对应页面时传 null
     */
    private Map<String, Object> stat(String label, long value, String unit, String link, String group) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("link", link);
        m.put("group", group);
        return m;
    }

    /** 组装待办项（label/count/link/priority） */
    private Map<String, Object> todo(String label, long count, String link, String priority) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("count", count);
        m.put("link", link);
        m.put("priority", priority);
        return m;
    }
}
