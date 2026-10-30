package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.dto.AiToolCallRequest;
import cn.greensmartplanet.gsc.ai.dto.AiToolCallResponse;
import cn.greensmartplanet.gsc.ai.dto.AiToolConfirmResult;
import cn.greensmartplanet.gsc.ai.dto.AiToolProposal;
import cn.greensmartplanet.gsc.ai.dto.ContextEntityDTO;
import cn.greensmartplanet.gsc.ai.service.AiChatService;
import cn.greensmartplanet.gsc.ai.support.AiToolProposalService;
import cn.greensmartplanet.gsc.ai.service.AiToolService;
import cn.greensmartplanet.gsc.ai.tool.AiToolRegistry;
import cn.greensmartplanet.gsc.api.delivery.AiDeliveryClient;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryOperateRequest;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryQuery;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;
import cn.greensmartplanet.gsc.api.supplier.AiSupplierClient;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteCreateRequest;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierRiskDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSearchQuery;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSummaryDTO;
import cn.greensmartplanet.gsc.api.trade.AiContractClient;
import cn.greensmartplanet.gsc.api.trade.AiInquiryClient;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryDeadlineRequest;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.weather.GeoLocation;
import cn.greensmartplanet.gsc.framework.weather.WeatherClient;
import cn.greensmartplanet.gsc.framework.weather.WeatherDaily;
import cn.greensmartplanet.gsc.framework.weather.WeatherForecast;
import cn.greensmartplanet.gsc.framework.weather.WeatherNow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具执行服务实现 — 校验工具名 + 参数解析 + 分发到供应商/交易域 Feign
 * <p>
 * 每个只读工具都做「参数必填校验 + 类型安全转换」，模型传入的非法值一律抛
 * AI_TOOL_ARG_INVALID（不信任模型参数，防注入/越权）。
 * <p>
 * list_contracts 的 tenantId 强制取登录态，忽略模型传入的 arguments 里的任何 tenantId。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolServiceImpl implements AiToolService {

    private final AiToolRegistry toolRegistry;
    private final AiSupplierClient aiSupplierClient;
    private final AiContractClient aiContractClient;
    private final AiInquiryClient aiInquiryClient;
    private final AiDeliveryClient aiDeliveryClient;
    private final AiToolProposalService aiToolProposalService;
    private final AiChatService aiChatService;
    private final WeatherClient weatherClient;

    /**
     * 列表类工具单次返回条数上限。
     * <p>
     * pageSize 由模型生成，不封顶时模型传 500 就会把整页明细灌进上下文（工具结果没有预算约束）；
     * 这里做源头闸门，Python 侧再做字段投影与字符兜底，两层配合。
     */
    static final int MAX_PAGE_SIZE = 20;

    /**
     * 工具分发入口。
     */
    @Override
    public AiToolCallResponse execute(AiToolCallRequest request, Long tenantId, Long userId, Long assistantMessageId) {
        // 1. 工具名非空 + 已注册校验（未知工具直接拒，防模型胡诌工具名）
        String toolName = request == null ? null : request.getToolName();
        toolRegistry.requireKnown(toolName);
        Map<String, Object> args = request.getArguments() == null ? Map.of() : request.getArguments();
        // 2. 按工具名分发，各分支自行解析参数（tenantId/userId 由登录态传入，模型不可伪造）
        //    业务异常向上抛，由框架统一转错误响应；Python 侧按失败结果转自然语言
        return switch (toolName) {
            case AiToolRegistry.SEARCH_SUPPLIERS -> searchSuppliers(args, tenantId);
            case AiToolRegistry.GET_SUPPLIER_DETAIL -> getSupplierDetail(args, tenantId);
            case AiToolRegistry.GET_MY_PROFILE -> getMyProfile(tenantId);
            case AiToolRegistry.LIST_CONTRACTS -> listContracts(args, tenantId);
            case AiToolRegistry.LIST_INQUIRIES -> listInquiries(args, tenantId);
            case AiToolRegistry.CREATE_SUPPLIER_NOTE -> createSupplierNote(args, tenantId, userId, assistantMessageId);
            case AiToolRegistry.UPDATE_INQUIRY -> updateInquiry(args, tenantId, userId, assistantMessageId);
            case AiToolRegistry.EXTEND_INQUIRY_DEADLINE -> extendInquiryDeadline(args, tenantId, userId, assistantMessageId);
            case AiToolRegistry.LIST_DELIVERIES -> listDeliveries(args, tenantId);
            case AiToolRegistry.GET_DELIVERY_DETAIL -> getDeliveryDetail(args, tenantId);
            case AiToolRegistry.SUBMIT_PROMISE_DATE -> submitPromiseDate(args, tenantId, userId, assistantMessageId);
            case AiToolRegistry.FILL_RETURN_LOGISTICS -> fillReturnLogistics(args, tenantId, userId, assistantMessageId);
            case AiToolRegistry.RISK_ASSESS_SUPPLIER -> riskAssessSupplier(args, tenantId);
            case AiToolRegistry.GET_WEATHER -> getWeather(args);
            default -> throw new BizException(ErrorCode.AI_TOOL_UNKNOWN);
        };
    }

    /**
     * search_suppliers：按关键词搜索供应商摘要。
     */
    private AiToolCallResponse searchSuppliers(Map<String, Object> args, Long tenantId) {
        // 关键词必填：无关键词的模糊搜索语义不成立，直接拒
        String keyword = requireString(args, "keyword");
        int page = parseIntOrDefault(args, "page", 1);
        int pageSize = resolvePageSize(args);
        // Feign 查询：分页参数由 Service 层兜底，AI 域只透传
        List<AiSupplierSummaryDTO> result = aiSupplierClient.searchSuppliers(
                AiSupplierSearchQuery.builder().keyword(keyword).page(page).pageSize(pageSize).build())
                .getData();
        log.info("AI 工具 search_suppliers 完成: keyword={}, count={}", keyword,
                result == null ? 0 : result.size());
        return AiToolCallResponse.builder().success(true).result(result).build();
    }

    /**
     * get_supplier_detail：按租户ID查供应商详情（含资质 + 准入状态）。
     */
    private AiToolCallResponse getSupplierDetail(Map<String, Object> args, Long tenantId) {
        // 供应商租户ID：模型传入的是雪花ID字符串，需转 Long；非法抛参
        Long supplierTenantId = requireLong(args, "tenantId");
        AiSupplierDetailDTO detail = aiSupplierClient.getSupplierDetail(supplierTenantId).getData();
        log.info("AI 工具 get_supplier_detail 完成: supplierTenantId={}, found={}",
                supplierTenantId, detail != null);
        // 查无此人（detail=null）不产实体，避免把「查不到」写成有效上下文
        return AiToolCallResponse.builder()
                .success(true)
                .result(detail)
                .entities(detail == null
                        ? List.of()
                        : List.of(entity("supplier", supplierTenantId, detail.getCompanyName())))
                .build();
    }

    /**
     * risk_assess_supplier：评估指定供应商的三维健康分（交付/质量/合规）。
     * <p>
     * 只读工具。评分算法在供应商域内聚，本处仅透传 tenantId、把结果喂回模型作答。
     * 不存在返回 result=null，模型据 data=null 组织「查无此供应商」回答。
     */
    private AiToolCallResponse riskAssessSupplier(Map<String, Object> args, Long tenantId) {
        Long supplierTenantId = requireLong(args, "tenantId");
        AiSupplierRiskDTO risk = aiSupplierClient.assessSupplierRisk(supplierTenantId).getData();
        log.info("AI 工具 risk_assess_supplier 完成: supplierTenantId={}, found={}",
                supplierTenantId, risk != null);
        return AiToolCallResponse.builder().success(true).result(risk).build();
    }

    /**
     * get_my_profile：查当前登录用户自己的企业画像（采购方/供应商均适用）。
     * <p>
     * 无模型入参——tenantId 强制取登录态，模型不可伪造（红线 2）。
     * 复用 get_supplier_detail 的同一 Feign 方法（getSupplierDetail 按 tenantId 查，
     * 不区分 buyer/supplier），只是调用入口从「模型传他人 tenantId」换成「后端取自己 tenantId」。
     * 返回 DTO 含 profileType，AI 据此措辞「采购方画像 / 供应商画像」。
     */
    private AiToolCallResponse getMyProfile(Long tenantId) {
        AiSupplierDetailDTO detail = aiSupplierClient.getSupplierDetail(tenantId).getData();
        log.info("AI 工具 get_my_profile 完成: tenantId={}, found={}", tenantId, detail != null);
        return AiToolCallResponse.builder().success(true).result(detail).build();
    }

    /**
     * list_contracts：查当前登录租户自己的合同（buyer/supplier 双视角）。
     */
    private AiToolCallResponse listContracts(Map<String, Object> args, Long tenantId) {
        // tenantId 强制取登录态，忽略模型 arguments 里可能夹带的 tenantId（防越权，红线 2）
        String status = optionalString(args, "status");
        String keyword = optionalString(args, "keyword");
        String role = optionalString(args, "role");
        int page = parseIntOrDefault(args, "page", 1);
        int pageSize = resolvePageSize(args);
        List<AiContractSummaryDTO> result = aiContractClient.listContracts(
                AiContractQuery.builder()
                        .tenantId(tenantId)
                        .status(status)
                        .keyword(keyword)
                        .role(role)
                        .page(page)
                        .pageSize(pageSize)
                        .build())
                .getData();
        log.info("AI 工具 list_contracts 完成: tenantId={}, role={}, count={}",
                tenantId, role, result == null ? 0 : result.size());
        // 整页合同进上下文（保留列表顺序），供下一轮「第一条 / 第二条」定位
        return AiToolCallResponse.builder()
                .success(true)
                .result(result)
                .entities(contractEntities(result))
                .build();
    }

    /**
     * list_inquiries：查当前登录租户自己的询盘（buyer/supplier 双视角），供写操作前定位 inquiryId。
     */
    private AiToolCallResponse listInquiries(Map<String, Object> args, Long tenantId) {
        // tenantId 强制取登录态，忽略模型 arguments 里可能夹带的 tenantId（防越权，红线 2）
        String status = optionalString(args, "status");
        String keyword = optionalString(args, "keyword");
        String role = optionalString(args, "role");
        int page = parseIntOrDefault(args, "page", 1);
        int pageSize = resolvePageSize(args);
        List<AiInquirySummaryDTO> result = aiInquiryClient.listInquiries(
                AiInquiryQuery.builder()
                        .tenantId(tenantId)
                        .status(status)
                        .keyword(keyword)
                        .role(role)
                        .page(page)
                        .pageSize(pageSize)
                        .build())
                .getData();
        log.info("AI 工具 list_inquiries 完成: tenantId={}, role={}, count={}",
                tenantId, role, result == null ? 0 : result.size());
        // 整页询盘进上下文（保留列表顺序），供下一轮「第一条 / 第二条」定位
        return AiToolCallResponse.builder()
                .success(true)
                .result(result)
                .entities(inquiryEntities(result))
                .build();
    }

    /**
     * update_inquiry：写工具，生成「修改询盘字段」提案，不落库。
     * <p>
     * 确认文案直接列「改什么字段→什么值」，让用户点确认前看清改了什么。
     */
    private AiToolCallResponse updateInquiry(Map<String, Object> args, Long tenantId, Long userId, Long assistantMessageId) {
        // 询盘ID必填（模型先从 list_inquiries 查得）；字段子集可选，其余为 null 表「不改」
        Long inquiryId = requireLong(args, "inquiryId");
        AiInquiryUpdateRequest fields = AiInquiryUpdateRequest.builder()
                .title(optionalString(args, "title"))
                .category(optionalString(args, "category"))
                .quantity(optionalBigDecimal(args, "quantity"))
                .unit(optionalString(args, "unit"))
                .deliveryPlace(optionalString(args, "deliveryPlace"))
                .deliveryDate(optionalLocalDate(args, "deliveryDate"))
                .paymentTerms(optionalString(args, "paymentTerms"))
                .specText(optionalString(args, "specText"))
                .build();
        String proposalId = aiToolProposalService.save(AiToolProposal.builder()
                .toolName(AiToolRegistry.UPDATE_INQUIRY)
                .tenantId(tenantId)
                .userId(userId)
                .inquiryId(inquiryId)
                .inquiryFields(fields)
                .assistantMessageId(assistantMessageId)
                .build());
        log.info("AI 工具 update_inquiry 已生成提案: tenantId={}, inquiryId={}, proposalId={}",
                tenantId, inquiryId, proposalId);
        // 逐字段拼「改什么」，无字段变更时退化为提示
        StringBuilder sb = new StringBuilder();
        appendField(sb, "标题", fields.getTitle());
        appendField(sb, "品类", fields.getCategory());
        appendField(sb, "数量", fields.getQuantity());
        appendField(sb, "单位", fields.getUnit());
        appendField(sb, "交货地点", fields.getDeliveryPlace());
        appendField(sb, "交付日期", fields.getDeliveryDate());
        appendField(sb, "付款条件", fields.getPaymentTerms());
        appendField(sb, "规格条款", fields.getSpecText());
        String change = sb.length() > 0 ? sb.toString() : "修改字段";
        // 查一次询盘摘要：确认文案展示「编号 + 标题」与上下文实体回填共用，避免重复 Feign
        AiInquirySummaryDTO summary = fetchInquirySummary(tenantId, inquiryId);
        return AiToolCallResponse.builder()
                .success(true)
                .requireConfirm(true)
                .proposalId(proposalId)
                .actionSummary(buildInquiryActionText(summary, inquiryId, "修改询盘", change))
                .entities(List.of(inquiryEntity(summary, inquiryId)))
                .build();
    }

    /**
     * extend_inquiry_deadline：写工具，生成「延长询盘报价截止」提案，不落库。
     * <p>
     * 独立于 update_inquiry：延长截止语义不同（支持 open/quoting/expired），映射已有 extendDeadline。
     */
    private AiToolCallResponse extendInquiryDeadline(Map<String, Object> args, Long tenantId, Long userId, Long assistantMessageId) {
        Long inquiryId = requireLong(args, "inquiryId");
        LocalDateTime newDeadline = requireLocalDateTime(args, "newDeadline");
        String proposalId = aiToolProposalService.save(AiToolProposal.builder()
                .toolName(AiToolRegistry.EXTEND_INQUIRY_DEADLINE)
                .tenantId(tenantId)
                .userId(userId)
                .inquiryId(inquiryId)
                .newDeadline(newDeadline)
                .assistantMessageId(assistantMessageId)
                .build());
        log.info("AI 工具 extend_inquiry_deadline 已生成提案: tenantId={}, inquiryId={}, proposalId={}",
                tenantId, inquiryId, proposalId);
        // 查一次询盘摘要：确认文案展示「编号 + 标题」与上下文实体回填共用，避免重复 Feign
        AiInquirySummaryDTO summary = fetchInquirySummary(tenantId, inquiryId);
        return AiToolCallResponse.builder()
                .success(true)
                .requireConfirm(true)
                .proposalId(proposalId)
                .actionSummary(buildInquiryActionText(summary, inquiryId, "延长询盘报价截止",
                        "截止时间改为 " + newDeadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))))
                .entities(List.of(inquiryEntity(summary, inquiryId)))
                .build();
    }

    /**
     * list_deliveries：查当前登录租户自己的交付单（buyer/supplier 双视角）。
     */
    private AiToolCallResponse listDeliveries(Map<String, Object> args, Long tenantId) {
        // tenantId 强制取登录态，忽略模型 arguments 里可能夹带的 tenantId（防越权，红线 2）
        String status = optionalString(args, "status");
        String keyword = optionalString(args, "keyword");
        String role = optionalString(args, "role");
        Long contractId = optionalLong(args, "contractId");
        Long supplierTenantId = optionalLong(args, "supplierTenantId");
        int page = parseIntOrDefault(args, "page", 1);
        int pageSize = resolvePageSize(args);
        List<AiDeliverySummaryDTO> result = aiDeliveryClient.listDeliveries(
                AiDeliveryQuery.builder()
                        .tenantId(tenantId)
                        .role(role)
                        .status(status)
                        .keyword(keyword)
                        .contractId(contractId)
                        .supplierTenantId(supplierTenantId)
                        .page(page)
                        .pageSize(pageSize)
                        .build())
                .getData();
        log.info("AI 工具 list_deliveries 完成: tenantId={}, role={}, count={}",
                tenantId, role, result == null ? 0 : result.size());
        // 整页交付单进上下文（保留列表顺序），供下一轮「第一条 / 第二条」定位
        return AiToolCallResponse.builder()
                .success(true)
                .result(result)
                .entities(deliveryEntities(result))
                .build();
    }

    /**
     * get_delivery_detail：按交付单ID查详情（含履约时间线 + 退货记录）。
     */
    private AiToolCallResponse getDeliveryDetail(Map<String, Object> args, Long tenantId) {
        Long deliveryId = requireLong(args, "deliveryId");
        AiDeliveryDetailDTO detail = aiDeliveryClient.getDelivery(
                AiDeliveryQuery.builder().tenantId(tenantId).deliveryId(deliveryId).build()).getData();
        log.info("AI 工具 get_delivery_detail 完成: deliveryId={}, found={}",
                deliveryId, detail != null);
        // 详情 DTO 继承摘要，自带单号/标题，无需再查一次
        return AiToolCallResponse.builder()
                .success(true)
                .result(detail)
                .entities(detail == null
                        ? List.of()
                        : List.of(entity("delivery", detail.getId(),
                                firstNonBlank(detail.getDeliveryNo(), detail.getContractTitle()))))
                .build();
    }

    /**
     * get_weather：按地点查实时天气 + 预报。地名清洗与页面同一套，失败不抛给主对话。
     */
    private AiToolCallResponse getWeather(Map<String, Object> args) {
        String city = requireString(args, "city");
        int days = Math.min(Math.max(parseIntOrDefault(args, "days", 3), 1), 7);
        GeoLocation geo = weatherClient.geocode(city);
        if (geo == null || geo.getLatitude() == null || geo.getLongitude() == null) {
            return AiToolCallResponse.builder()
                    .success(false)
                    .error("未解析到该地名：" + city + "。请改用城市名，如 杭州、广州、Shanghai")
                    .build();
        }
        WeatherNow now = weatherClient.now(geo.getLatitude(), geo.getLongitude());
        WeatherForecast forecast = weatherClient.forecast(geo.getLatitude(), geo.getLongitude(), days);
        if (now == null && (forecast == null || forecast.getDaily() == null || forecast.getDaily().isEmpty())) {
            return AiToolCallResponse.builder()
                    .success(false)
                    .error("天气数据源暂时不可用，请稍后重试")
                    .build();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalQuery", city);
        result.put("resolvedPlace", geo.getResolvedPlace() != null ? geo.getResolvedPlace() : geo.getName());
        if (now != null) {
            result.put("weatherText", now.getWeatherText());
            result.put("temperature", now.getTemperature());
            result.put("apparentTemperature", now.getApparentTemperature());
            result.put("humidity", now.getHumidity());
            result.put("precipitation", now.getPrecipitation());
            result.put("windGrade", now.getWindGrade());
            result.put("windDirectionText", now.getWindDirectionText());
            result.put("observeTime", now.getTime());
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        if (forecast != null && forecast.getDaily() != null) {
            for (WeatherDaily row : forecast.getDaily()) {
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", row.getDate());
                day.put("weatherText", row.getWeatherText());
                day.put("tempMin", row.getTempMin());
                day.put("tempMax", row.getTempMax());
                day.put("precipitationSum", row.getPrecipitationSum());
                day.put("windGradeMax", row.getWindGradeMax());
                daily.add(day);
            }
        }
        result.put("daily", daily);
        log.info("AI 工具 get_weather 完成: city={}, resolved={}", city, result.get("resolvedPlace"));
        return AiToolCallResponse.builder().success(true).result(result).build();
    }

    /**
     * submit_promise_date：写工具，生成「提交承诺交期」提案，不落库。
     */
    private AiToolCallResponse submitPromiseDate(Map<String, Object> args, Long tenantId, Long userId, Long assistantMessageId) {
        Long deliveryId = requireLong(args, "deliveryId");
        LocalDate promisedDate = requireLocalDate(args, "promisedDate");
        String reason = optionalString(args, "reason");
        String proposalId = aiToolProposalService.save(AiToolProposal.builder()
                .toolName(AiToolRegistry.SUBMIT_PROMISE_DATE)
                .tenantId(tenantId)
                .userId(userId)
                .deliveryId(deliveryId)
                .promisedDate(promisedDate)
                .reason(reason)
                .assistantMessageId(assistantMessageId)
                .build());
        log.info("AI 工具 submit_promise_date 已生成提案: tenantId={}, deliveryId={}, proposalId={}",
                tenantId, deliveryId, proposalId);
        // 查一次交付单摘要：确认文案展示单号与上下文实体回填共用，避免重复 Feign
        AiDeliverySummaryDTO summary = fetchDeliverySummary(tenantId, deliveryId);
        return AiToolCallResponse.builder()
                .success(true)
                .requireConfirm(true)
                .proposalId(proposalId)
                .actionSummary(buildDeliveryActionText(summary, deliveryId, "提交承诺交期",
                        "承诺交期 " + promisedDate))
                .entities(List.of(deliveryEntity(summary, deliveryId)))
                .build();
    }

    /**
     * fill_return_logistics：写工具，生成「填写退货物流」提案，不落库。
     */
    private AiToolCallResponse fillReturnLogistics(Map<String, Object> args, Long tenantId, Long userId, Long assistantMessageId) {
        Long deliveryId = requireLong(args, "deliveryId");
        String logisticsCompany = requireString(args, "logisticsCompany");
        String trackingNo = requireString(args, "trackingNo");
        String proposalId = aiToolProposalService.save(AiToolProposal.builder()
                .toolName(AiToolRegistry.FILL_RETURN_LOGISTICS)
                .tenantId(tenantId)
                .userId(userId)
                .deliveryId(deliveryId)
                .logisticsCompany(logisticsCompany)
                .trackingNo(trackingNo)
                .assistantMessageId(assistantMessageId)
                .build());
        log.info("AI 工具 fill_return_logistics 已生成提案: tenantId={}, deliveryId={}, proposalId={}",
                tenantId, deliveryId, proposalId);
        // 查一次交付单摘要：确认文案展示单号与上下文实体回填共用，避免重复 Feign
        AiDeliverySummaryDTO summary = fetchDeliverySummary(tenantId, deliveryId);
        return AiToolCallResponse.builder()
                .success(true)
                .requireConfirm(true)
                .proposalId(proposalId)
                .actionSummary(buildDeliveryActionText(summary, deliveryId, "填写退货物流",
                        logisticsCompany + " / " + trackingNo))
                .entities(List.of(deliveryEntity(summary, deliveryId)))
                .build();
    }

    /**
     * 查交付单摘要：确认文案展示单号与上下文实体回填共用，避免重复 Feign。
     * <p>
     * 查询不到（交付单被删/非本人）返回 null，调用方退化处理，确认后仍由交付域兜底校验归属。
     */
    private AiDeliverySummaryDTO fetchDeliverySummary(Long tenantId, Long deliveryId) {
        try {
            List<AiDeliverySummaryDTO> rows = aiDeliveryClient.listDeliveries(
                    AiDeliveryQuery.builder().tenantId(tenantId).deliveryId(deliveryId).build()).getData();
            return rows == null ? null : rows.stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("AI 交付单摘要查询失败，退化为裸ID: deliveryId={}, error={}", deliveryId, e.getMessage());
            return null;
        }
    }

    /** 拼交付单操作确认文案：展示「交付单 Dxxx」而非裸 ID。 */
    private String buildDeliveryActionText(AiDeliverySummaryDTO summary, Long deliveryId, String action, String change) {
        String target = summary != null && summary.getDeliveryNo() != null
                ? "交付单 " + summary.getDeliveryNo() : "交付单 #" + deliveryId;
        return change != null ? action + "：" + target + "，" + change : action + "：" + target;
    }

    /**
     * 查询盘摘要：确认文案展示「编号 + 标题」与上下文实体回填共用，避免重复 Feign。
     * <p>
     * 查询不到（询盘被删/非本人）返回 null，调用方退化处理，确认后仍由 trade 域兜底校验归属。
     */
    private AiInquirySummaryDTO fetchInquirySummary(Long tenantId, Long inquiryId) {
        try {
            return aiInquiryClient.getInquiry(
                    AiInquiryQuery.builder().tenantId(tenantId).inquiryId(inquiryId).build()).getData();
        } catch (Exception e) {
            log.warn("AI 询盘摘要查询失败，退化为裸ID: inquiryId={}, error={}", inquiryId, e.getMessage());
            return null;
        }
    }

    /** 拼询盘操作确认文案：展示「询盘 RFQxxx《标题》」而非裸 ID。 */
    private String buildInquiryActionText(AiInquirySummaryDTO summary, Long inquiryId, String action, String change) {
        String target = summary != null && summary.getInquiryNo() != null
                ? "询盘 " + summary.getInquiryNo() + titleSuffix(summary.getTitle())
                : "询盘 #" + inquiryId;
        return change != null ? action + "：" + target + "，" + change : action + "：" + target;
    }

    /**
     * 查供应商详情（写工具回填用）：失败只记 warn 返回 null，不阻断提案生成。
     * <p>
     * 与只读工具 get_supplier_detail 的区别：后者失败必须抛（工具执行失败），
     * 本方法仅回填确认文案与上下文，属展示性字段，降级放行（规范 8.1 红线 6 降级二分）。
     */
    private AiSupplierDetailDTO tryFetchSupplierDetail(Long supplierTenantId) {
        try {
            return aiSupplierClient.getSupplierDetail(supplierTenantId).getData();
        } catch (Exception e) {
            log.warn("AI 供应商摘要查询失败，退化为裸ID: supplierTenantId={}, error={}",
                    supplierTenantId, e.getMessage());
            return null;
        }
    }

    // ==================== 业务对象上下文实体（供 Python 指代消解） ====================

    /** 单对象实体：objectId 统一转字符串，防 JSON 精度丢失。 */
    private ContextEntityDTO entity(String objectType, Object objectId, String title) {
        return ContextEntityDTO.builder()
                .objectType(objectType)
                .objectId(objectId == null ? null : String.valueOf(objectId))
                .title(title)
                .build();
    }

    /** 询盘实体：标题缺失时退化用编号，仍便于下一轮定位。 */
    private ContextEntityDTO inquiryEntity(AiInquirySummaryDTO summary, Long inquiryId) {
        return entity("inquiry", inquiryId,
                summary == null ? null : firstNonBlank(summary.getTitle(), summary.getInquiryNo()));
    }

    /** 交付单实体：用交付单号做主指称（list_deliveries 的 keyword 可直接搜回），退化才用合同标题。 */
    private ContextEntityDTO deliveryEntity(AiDeliverySummaryDTO summary, Long deliveryId) {
        if (summary == null) {
            return entity("delivery", deliveryId, null);
        }
        return entity("delivery", summary.getId() != null ? summary.getId() : deliveryId,
                firstNonBlank(summary.getDeliveryNo(), summary.getContractTitle()));
    }

    /** 合同列表 → 上下文实体（保留列表顺序，供「第一条 / 第二条」按位定位）。 */
    private List<ContextEntityDTO> contractEntities(List<AiContractSummaryDTO> rows) {
        return rows == null ? List.of() : rows.stream()
                .map(row -> entity("contract", row.getId(), firstNonBlank(row.getTitle(), row.getContractNo())))
                .toList();
    }

    /** 询盘列表 → 上下文实体（保留列表顺序）。 */
    private List<ContextEntityDTO> inquiryEntities(List<AiInquirySummaryDTO> rows) {
        return rows == null ? List.of() : rows.stream()
                .map(row -> entity("inquiry", row.getId(), firstNonBlank(row.getTitle(), row.getInquiryNo())))
                .toList();
    }

    /** 交付单列表 → 上下文实体（保留列表顺序）。 */
    private List<ContextEntityDTO> deliveryEntities(List<AiDeliverySummaryDTO> rows) {
        return rows == null ? List.of() : rows.stream()
                .map(row -> entity("delivery", row.getId(),
                        firstNonBlank(row.getDeliveryNo(), row.getContractTitle())))
                .toList();
    }

    /** 取第一个非空白值（标题缺失时退化用编号）。 */
    private String firstNonBlank(String first, String second) {
        return (first != null && !first.isBlank()) ? first : second;
    }

    /** 《标题》后缀（标题为空返回空串）。 */
    private String titleSuffix(String title) {
        return (title != null && !title.isBlank()) ? "《" + title + "》" : "";
    }

    /**
     * 字段变更描述累加器（value 非 null 才追加，保持文案只列实际要改的字段）。
     */
    private void appendField(StringBuilder sb, String label, Object value) {
        if (value == null) return;
        if (sb.length() > 0) sb.append("、");
        sb.append(label).append("改为 ").append(value);
    }

    /**
     * create_supplier_note：写工具，不落库，只生成待确认提案返回 Python。
     * <p>
     * 安全边界：模型只能发起提案，落库由确认接口在用户点击确认后触发；
     * supplierTenantId/content 存进提案，tenantId/userId 由登录态注入，模型无法伪造归属。
     */
    private AiToolCallResponse createSupplierNote(Map<String, Object> args, Long tenantId, Long userId, Long assistantMessageId) {
        // 供应商租户ID + 备注内容必填（模型参数，非法抛参）
        Long supplierTenantId = requireLong(args, "supplierTenantId");
        String content = requireString(args, "content");
        // 生成提案存 Redis，返回 proposalId 供前端确认回传
        String proposalId = aiToolProposalService.save(AiToolProposal.builder()
                .toolName(AiToolRegistry.CREATE_SUPPLIER_NOTE)
                .tenantId(tenantId)
                .userId(userId)
                .supplierTenantId(supplierTenantId)
                .content(content)
                .assistantMessageId(assistantMessageId)
                .build());
        log.info("AI 工具 create_supplier_note 已生成提案: tenantId={}, supplierTenantId={}, proposalId={}",
                tenantId, supplierTenantId, proposalId);
        // 查一次供应商名：确认文案要说明「给哪家备注」而非裸 ID（规范 8.3 铁律 4），同时回填上下文实体
        AiSupplierDetailDTO supplier = tryFetchSupplierDetail(supplierTenantId);
        String supplierName = supplier == null ? null : supplier.getCompanyName();
        // requireConfirm=true 让 Python 短路返回确认事件，不再让模型自由发挥
        return AiToolCallResponse.builder()
                .success(true)
                .requireConfirm(true)
                .proposalId(proposalId)
                .actionSummary("记录供应商备注："
                        + (supplierName != null ? supplierName : "#" + supplierTenantId)
                        + "，内容：" + content)
                .entities(List.of(entity("supplier", supplierTenantId, supplierName)))
                .build();
    }

    /**
     * 确认并执行写操作提案（前端确认按钮触发）。
     * <p>
     * 从 Redis 取出并消费提案（读后即删，防重放 + 归属校验），再按工具名分发落库。
     * 真正的数据变更在 supplier/trade 域发生，本方法只做提案消费与分发。
     * 落库成功后把流式阶段兜底的「已发起操作，请确认执行。」占位覆盖成最终文案。
     */
    @Override
    public AiToolConfirmResult confirm(String proposalId, Long tenantId, Long userId) {
        // 1. 取出提案（读后即删 + 归属校验，防越权确认他人提案）
        AiToolProposal proposal = aiToolProposalService.take(proposalId, tenantId, userId);
        // 2. 按工具名分发落库，落库结果组装成最终展示文案
        String finalText;
        try {
            finalText = dispatchProposal(proposal);
        } catch (RuntimeException e) {
            // 下游 Feign 失败：提案已被 take 删除，不放回则用户重试只会得到「提案不存在」，
            // 占位气泡永久停在「已发起操作，请确认执行。」。放回后用户可直接再点一次确认。
            aiToolProposalService.restore(proposalId, proposal);
            throw e;
        }
        // 3. 覆盖占位气泡为最终文案（assistantMessageId 为空时跳过，如直连验证路径）
        Long assistantMessageId = proposal.getAssistantMessageId();
        aiChatService.updateConfirmResult(assistantMessageId, tenantId, finalText);
        return AiToolConfirmResult.builder()
                .finalText(finalText)
                .assistantMessageId(assistantMessageId)
                .build();
    }

    /**
     * 按提案的工具名分发到对应域的 Feign 落库，返回最终展示文案。
     * <p>
     * 抽成独立方法是为了让 {@link #confirm} 能把「分发」整体包进 try-catch——
     * 任一分支抛异常都要把提案放回 Redis 供重试。
     *
     * @param proposal 已通过归属校验的提案
     * @return 面向用户的最终文案
     */
    private String dispatchProposal(AiToolProposal proposal) {
        String finalText;
        switch (proposal.getToolName()) {
            case AiToolRegistry.CREATE_SUPPLIER_NOTE -> {
                // 调用供应商域 Feign 真正落库（ownerTenantId/createBy 从登录态注入）
                AiSupplierNoteCreateRequest request = AiSupplierNoteCreateRequest.builder()
                        .ownerTenantId(proposal.getTenantId())
                        .supplierTenantId(proposal.getSupplierTenantId())
                        .content(proposal.getContent())
                        .createBy(proposal.getUserId())
                        .build();
                aiSupplierClient.createSupplierNote(request);
                finalText = "已记录供应商备注：" + proposal.getContent();
            }
            case AiToolRegistry.UPDATE_INQUIRY -> {
                AiInquiryUpdateRequest request = proposal.getInquiryFields();
                request.setInquiryId(proposal.getInquiryId());
                request.setTenantId(proposal.getTenantId());
                request.setUserId(proposal.getUserId());
                aiInquiryClient.updateInquiry(request);
                finalText = "已完成操作：修改询盘";
            }
            case AiToolRegistry.EXTEND_INQUIRY_DEADLINE -> {
                aiInquiryClient.extendInquiryDeadline(AiInquiryDeadlineRequest.builder()
                        .inquiryId(proposal.getInquiryId())
                        .tenantId(proposal.getTenantId())
                        .userId(proposal.getUserId())
                        .newDeadline(proposal.getNewDeadline())
                        .build());
                finalText = "已完成操作：延长询盘报价截止";
            }
            case AiToolRegistry.SUBMIT_PROMISE_DATE -> {
                aiDeliveryClient.submitPromiseDate(AiDeliveryOperateRequest.builder()
                        .deliveryId(proposal.getDeliveryId())
                        .tenantId(proposal.getTenantId())
                        .userId(proposal.getUserId())
                        .promisedDate(proposal.getPromisedDate())
                        .reason(proposal.getReason())
                        .build());
                finalText = "已完成操作：提交承诺交期";
            }
            case AiToolRegistry.FILL_RETURN_LOGISTICS -> {
                aiDeliveryClient.fillReturnLogistics(AiDeliveryOperateRequest.builder()
                        .deliveryId(proposal.getDeliveryId())
                        .tenantId(proposal.getTenantId())
                        .userId(proposal.getUserId())
                        .logisticsCompany(proposal.getLogisticsCompany())
                        .trackingNo(proposal.getTrackingNo())
                        .build());
                finalText = "已完成操作：填写退货物流";
            }
            default -> throw new BizException(ErrorCode.AI_TOOL_UNKNOWN);
        }
        return finalText;
    }

    /**
     * 取消写操作提案（前端取消按钮触发，不落库）。
     * <p>
     * 只消费 Redis 提案（防事后误确认），不触发任何业务变更；
     * 把气泡占位覆盖成取消文案，历史里清晰记录「未做任何变更」。
     */
    @Override
    public AiToolConfirmResult cancel(String proposalId, Long tenantId, Long userId) {
        // 消费提案（读后即删 + 归属校验），丢弃内容
        AiToolProposal proposal = aiToolProposalService.take(proposalId, tenantId, userId);
        Long assistantMessageId = proposal.getAssistantMessageId();
        aiChatService.updateConfirmResult(assistantMessageId, tenantId, "好的，已取消该操作，未做任何变更。");
        return AiToolConfirmResult.builder()
                .finalText("好的，已取消该操作，未做任何变更。")
                .assistantMessageId(assistantMessageId)
                .build();
    }

    // ==================== 参数解析工具方法 ====================

    /**
     * 读取必填字符串参数（缺失/空/非字符串一律抛 AI_TOOL_ARG_INVALID）。
     */
    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不能为空");
        }
        return String.valueOf(value);
    }

    /**
     * 读取可选字符串参数（缺失返回 null）。
     */
    private String optionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 读取必填 Long 参数（模型传入的是雪花ID字符串，兼容 number/string；非法抛 AI_TOOL_ARG_INVALID）。
     */
    private Long requireLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不能为空");
        }
        try {
            return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的ID");
        }
    }

    /**
     * 读取可选 Long 参数（缺失返回 null，非法抛 AI_TOOL_ARG_INVALID）。
     */
    private Long optionalLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        try {
            return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的ID");
        }
    }

    /**
     * 读取整数参数，缺失用默认值，非法抛 AI_TOOL_ARG_INVALID。
     */
    /**
     * 解析 pageSize 并封顶（{@link #MAX_PAGE_SIZE}），防模型传入超大值把明细灌满上下文。
     * <p>
     * 封顶时打日志：模型拿到的条数比它要求少，排查「为什么只返回 10 条」时要能看到被静默改写。
     *
     * @param args 模型参数
     * @return 有效 pageSize，范围 [1, MAX_PAGE_SIZE]
     */
    int resolvePageSize(Map<String, Object> args) {
        int requested = parseIntOrDefault(args, "pageSize", 10);
        int effective = Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
        if (effective != requested) {
            log.info("工具 pageSize 被约束: 请求={}, 实际={}, 上限={}", requested, effective, MAX_PAGE_SIZE);
        }
        return effective;
    }

    private int parseIntOrDefault(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的整数");
        }
    }

    /**
     * 读取可选 BigDecimal 参数（缺失返回 null，非法抛 AI_TOOL_ARG_INVALID）。
     */
    private BigDecimal optionalBigDecimal(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        try {
            return value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的数字");
        }
    }

    /**
     * 读取可选 LocalDate 参数（缺失返回 null，非法抛 AI_TOOL_ARG_INVALID）。
     */
    private LocalDate optionalLocalDate(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的日期（yyyy-MM-dd）");
        }
    }

    /**
     * 读取必填 LocalDate 参数（缺失抛 AI_TOOL_ARG_INVALID）。
     */
    private LocalDate requireLocalDate(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不能为空");
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的日期（yyyy-MM-dd）");
        }
    }

    /**
     * 读取可选 LocalDateTime 参数（缺失返回 null，非法抛 AI_TOOL_ARG_INVALID）。
     */
    private LocalDateTime optionalLocalDateTime(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        return parseLocalDateTime(value, key);
    }

    /**
     * 读取必填 LocalDateTime 参数（缺失抛 AI_TOOL_ARG_INVALID）。
     */
    private LocalDateTime requireLocalDateTime(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不能为空");
        }
        return parseLocalDateTime(value, key);
    }

    /**
     * 解析 LocalDateTime：兼容 yyyy-MM-dd HH:mm:ss（空格转 T）与 ISO 格式，非法抛 AI_TOOL_ARG_INVALID。
     */
    private LocalDateTime parseLocalDateTime(Object value, String key) {
        try {
            String text = String.valueOf(value).trim().replace(' ', 'T');
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_TOOL_ARG_INVALID, "参数 " + key + " 不是合法的日期时间（yyyy-MM-dd HH:mm:ss）");
        }
    }
}
