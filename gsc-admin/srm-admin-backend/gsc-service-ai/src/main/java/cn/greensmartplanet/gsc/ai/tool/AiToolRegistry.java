package cn.greensmartplanet.gsc.ai.tool;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 工具注册表 — 只读工具清单的唯一权威来源
 * <p>
 * 职责：声明每个工具的元数据（名称/描述/参数 schema）并按名查找。
 * 工具的实际执行在 AiToolService（注册表只负责"有哪些工具"与"叫什么"）。
 * <p>
 * Phase 1 只注册 3 个只读工具；Phase 4/5 新增写工具时在此追加定义，参数 schema 同步喂给模型。
 *
 * @author gsc
 */
@Component
public class AiToolRegistry {

    /** 工具名常量：按名称搜索供应商 */
    public static final String SEARCH_SUPPLIERS = "search_suppliers";
    /** 工具名常量：按租户ID查供应商详情 */
    public static final String GET_SUPPLIER_DETAIL = "get_supplier_detail";
    /** 工具名常量：查当前登录用户自己的企业画像（buyer/supplier 均可） */
    public static final String GET_MY_PROFILE = "get_my_profile";
    /** 工具名常量：查当前租户的合同列表 */
    public static final String LIST_CONTRACTS = "list_contracts";
    /** 工具名常量：给供应商记备注（写操作，需二次确认 + 审计） */
    public static final String CREATE_SUPPLIER_NOTE = "create_supplier_note";
    /** 工具名常量：查当前租户的询盘列表（只读） */
    public static final String LIST_INQUIRIES = "list_inquiries";
    /** 工具名常量：修改询盘字段（写操作，需二次确认 + 审计） */
    public static final String UPDATE_INQUIRY = "update_inquiry";
    /** 工具名常量：延长询盘报价截止（写操作，需二次确认 + 审计） */
    public static final String EXTEND_INQUIRY_DEADLINE = "extend_inquiry_deadline";
    /** 工具名常量：查当前租户的交付单列表（只读） */
    public static final String LIST_DELIVERIES = "list_deliveries";
    /** 工具名常量：查交付单详情（只读） */
    public static final String GET_DELIVERY_DETAIL = "get_delivery_detail";
    /** 工具名常量：供应商提交承诺交期（写操作，需二次确认 + 审计） */
    public static final String SUBMIT_PROMISE_DATE = "submit_promise_date";
    /** 工具名常量：采购方填写退货物流（写操作，需二次确认 + 审计） */
    public static final String FILL_RETURN_LOGISTICS = "fill_return_logistics";
    /** 工具名常量：供应商风险评分（只读，三维健康分） */
    public static final String RISK_ASSESS_SUPPLIER = "risk_assess_supplier";
    /** 工具名常量：查某地实时天气与预报（只读） */
    public static final String GET_WEATHER = "get_weather";

    /** 工具清单（启动即构建，按名 O(1) 查找） */
    private final Map<String, AiToolDefinition> definitions;

    public AiToolRegistry() {
        this.definitions = buildDefinitions().stream()
                .collect(Collectors.toMap(AiToolDefinition::getName, Function.identity()));
    }

    /**
     * 按工具名查找定义（未注册返回 null，由调用方抛 AI_TOOL_UNKNOWN）。
     *
     * @param name 工具名
     * @return 工具定义，未知返回 null
     */
    public AiToolDefinition get(String name) {
        return definitions.get(name);
    }

    /** 全部工具定义（Phase 2 由 Python 拉取后喂给模型） */
    public List<AiToolDefinition> list() {
        return List.copyOf(definitions.values());
    }

    /**
     * 校验工具名已注册（未知工具拒绝执行，防模型胡诌工具名穿透到任意分支）。
     *
     * @param name 工具名
     */
    public void requireKnown(String name) {
        if (name == null || !definitions.containsKey(name)) {
            throw new BizException(ErrorCode.AI_TOOL_UNKNOWN);
        }
    }

    /**
     * 构建 4 个只读工具 + 4 个写工具定义。
     * <p>
     * parametersJson 为 OpenAI Function Calling 兼容 schema，字段命名用 camelCase 与契约 DTO 对齐。
     * create_supplier_note / update_inquiry / extend_inquiry_deadline / submit_promise_date / fill_return_logistics 为写工具：
     * Python 侧拿到 requireConfirm=true 时短路返回确认事件，不再继续让模型自由生成。
     * 授标/合同/验收/关闭/取消询盘等涉钱涉权动作不建工具，由模型引导用户手动执行。
     */
    private List<AiToolDefinition> buildDefinitions() {
        return List.of(
                AiToolDefinition.builder()
                        .name(SEARCH_SUPPLIERS)
                        .description("按关键词搜索供应商，返回名称/行业/品类/地区/资质/准入状态")
                        .parametersJson("""
                                {"type":"object","properties":{"keyword":{"type":"string","description":"关键词，匹配企业名/行业/地区/品类"},\
                                "page":{"type":"integer","description":"页码，从1开始"},"pageSize":{"type":"integer","description":"每页条数"}},\
                                "required":["keyword"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(GET_SUPPLIER_DETAIL)
                        .description("按供应商租户ID查详情，返回资质认证、尽调进度与准入状态")
                        .parametersJson("""
                                {"type":"object","properties":{"tenantId":{"type":"string","description":"供应商租户ID（雪花ID字符串）"}},\
                                "required":["tenantId"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(GET_MY_PROFILE)
                        .description("查当前登录用户自己的企业画像（采购方/供应商均适用），返回完整画像 + 尽调报告链接。无需参数，模型不传 tenantId，由后端从登录态取。")
                        .parametersJson("""
                                {"type":"object","properties":{},"required":[]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(LIST_CONTRACTS)
                        .description("查当前登录租户自己的合同列表（采购/供应双视角），返回编号/标题/金额/状态/交期。回答时状态用 statusText 中文（如已签署），不要输出英文 status 码")
                        .parametersJson("""
                                {"type":"object","properties":{"status":{"type":"string","description":"合同状态筛选"},"keyword":{"type":"string","description":"标题关键词"},\
                                "role":{"type":"string","description":"视角：buyer或supplier，默认buyer"},"page":{"type":"integer","description":"页码，从1开始"},\
                                "pageSize":{"type":"integer","description":"每页条数"}},"required":[]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(LIST_INQUIRIES)
                        .description("查当前登录租户自己的询盘列表（采购/供应双视角），返回询盘编号/标题/品类/数量/交期/截止/状态，用于定位要操作的询盘。回答时状态用 statusText 中文（如报价中），不要输出英文 status 码")
                        .parametersJson("""
                                {"type":"object","properties":{"status":{"type":"string","description":"询盘状态筛选"},"keyword":{"type":"string","description":"询盘编号/标题/品类关键词"},\
                                "role":{"type":"string","description":"视角：buyer或supplier，默认buyer"},"page":{"type":"integer","description":"页码，从1开始"},\
                                "pageSize":{"type":"integer","description":"每页条数"}},"required":[]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(CREATE_SUPPLIER_NOTE)
                        .description("给指定供应商记一条备注（采购商私有标注）。此操作需用户二次确认后才真正写入。")
                        .parametersJson("""
                                {"type":"object","properties":{"supplierTenantId":{"type":"string","description":"被备注的供应商租户ID（雪花ID字符串）"},\
                                "content":{"type":"string","description":"备注内容"}},\
                                "required":["supplierTenantId","content"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(UPDATE_INQUIRY)
                        .description("修改询盘的字段（交期/数量/付款条件/标题/品类/单位/交货地点/规格等）。只改传入的字段，未传字段保持不变。报价截止时间不能用本工具改，请用 extend_inquiry_deadline。此操作需用户二次确认后才真正写入。")
                        .parametersJson("""
                                {"type":"object","properties":{"inquiryId":{"type":"string","description":"询盘ID（雪花ID字符串，先从 list_inquiries 查得）"},\
                                "title":{"type":"string","description":"询盘标题"},"category":{"type":"string","description":"采购品类"},"quantity":{"type":"number","description":"采购数量"},\
                                "unit":{"type":"string","description":"单位"},"deliveryPlace":{"type":"string","description":"交货地点"},"deliveryDate":{"type":"string","description":"期望交付日期，格式 yyyy-MM-dd"},\
                                "paymentTerms":{"type":"string","description":"付款条件"},"specText":{"type":"string","description":"规格条款"}},\
                                "required":["inquiryId"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(EXTEND_INQUIRY_DEADLINE)
                        .description("延长询盘的报价截止时间（支持 open/quoting/expired 状态，已过期询盘延长后按有无报价恢复状态）。此操作需用户二次确认后才真正执行。")
                        .parametersJson("""
                                {"type":"object","properties":{"inquiryId":{"type":"string","description":"询盘ID（雪花ID字符串，先从 list_inquiries 查得）"},\
                                "newDeadline":{"type":"string","description":"新的报价截止时间，格式 yyyy-MM-dd HH:mm:ss"}},\
                                "required":["inquiryId","newDeadline"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(LIST_DELIVERIES)
                        .description("查当前登录租户自己的交付单列表（采购/供应双视角），返回交付单号/合同号/供应商/状态/运单号/承诺交期。回答时状态用 statusText 中文（如待发货），不要输出英文 status 码")
                        .parametersJson("""
                                {"type":"object","properties":{"status":{"type":"string","description":"状态筛选，逗号分隔，如 pending_shipment,shipped"},"keyword":{"type":"string","description":"交付单号/运单号关键字"},\
                                "role":{"type":"string","description":"视角：buyer或supplier，默认buyer"},"contractId":{"type":"string","description":"合同ID（雪花ID字符串）"},"supplierTenantId":{"type":"string","description":"供应商租户ID"},\
                                "page":{"type":"integer","description":"页码，从1开始"},"pageSize":{"type":"integer","description":"每页条数"}},"required":[]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(GET_DELIVERY_DETAIL)
                        .description("按交付单ID查详情（含履约时间线 + 物流事件 + 承诺记录 + 退货信息）。回答时状态用 statusText 中文，不要输出英文 status 码")
                        .parametersJson("""
                                {"type":"object","properties":{"deliveryId":{"type":"string","description":"交付单ID（雪花ID字符串，先从 list_deliveries 查得）"}},\
                                "required":["deliveryId"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(SUBMIT_PROMISE_DATE)
                        .description("供应商对未闭环的交付单提交承诺交期（同单已有待确认承诺时不可重复提交）。此操作需用户二次确认后才真正写入。")
                        .parametersJson("""
                                {"type":"object","properties":{"deliveryId":{"type":"string","description":"交付单ID（雪花ID字符串，先从 list_deliveries 查得）"},\
                                "promisedDate":{"type":"string","description":"承诺交期，格式 yyyy-MM-dd"},"reason":{"type":"string","description":"承诺原因（可选，如 原材料延迟/产能紧张；不填则用默认文案）"}},\
                                "required":["deliveryId","promisedDate"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(FILL_RETURN_LOGISTICS)
                        .description("采购方填写退货物流（仅交付单状态为 待退回货物 return_confirmed 时可用）。此操作需用户二次确认后才真正写入。")
                        .parametersJson("""
                                {"type":"object","properties":{"deliveryId":{"type":"string","description":"交付单ID（雪花ID字符串，先从 list_deliveries 查得）"},\
                                "logisticsCompany":{"type":"string","description":"退货物流公司，如 顺丰/中通"},"trackingNo":{"type":"string","description":"退货运单号"}},\
                                "required":["deliveryId","logisticsCompany","trackingNo"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(RISK_ASSESS_SUPPLIER)
                        .description("评估指定供应商的综合风险，返回交付/质量/合规三维健康分（0-100，越高越安全）、总评分与风险等级（low/medium/high）。")
                        .parametersJson("""
                                {"type":"object","properties":{"tenantId":{"type":"string","description":"待评估的供应商租户ID（雪花ID字符串，先从 search_suppliers 或 get_supplier_detail 查得）"}},\
                                "required":["tenantId"]}""")
                        .build(),
                AiToolDefinition.builder()
                        .name(GET_WEATHER)
                        .description("查某地实时天气与近几日预报（全球城市）。用户问天气、台风、暴雨、物流节点当地气候时调用。支持详细地点（如杭州西湖、广州天河智慧城），系统会抽成城市再查。问某票货在途天气时先 get_delivery_detail 取物流地点再调本工具。回答用中文，点名气温、风力、降水，不要编造未返回的灾害预警。")
                        .parametersJson("""
                                {"type":"object","properties":{"city":{"type":"string","description":"地点，城市或详细节点，如 上海 / 杭州西湖 / Shanghai"},\
                                "days":{"type":"integer","description":"预报天数 1-7，默认 3"}},\
                                "required":["city"]}""")
                        .build()
        );
    }
}
