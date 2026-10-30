package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.platform.service.ApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 尽调支付异步回调入口。
 * <p>
 * 下单时把本地址作为 {@code notify_url} 交给支付中台。当前是直连模式
 *（{@code notify_callback_type=1}）：用户扫码后由工行服务器 POST 到这里，
 * 不是浏览器请求，Network 面板里看不到。
 * <p>
 * 本接口只负责两件事：把工行/中台的包解析成字段，再交给
 * {@link ApplicationService#handlePaymentNotify} 对单入账。
 * 不登录、不改金额；成功必须回 HTTP 200，否则工行会重试。
 */
@Slf4j
@RestController
@RequestMapping("/api/pub/payment")
@RequiredArgsConstructor
@Tag(name = "支付回调", description = "公司支付中台异步通知")
public class PaymentCallbackController {

    private final ApplicationService applicationService;
    private final ObjectMapper objectMapper;

    /**
     * 工行付完后打到这里。入账成功后前端轮询才能跳到尽调进度页。
     *
     * @param request 原始回调，可能是 form-urlencoded，也可能是 JSON
     * @return 中台/工行认的成功包：{@code status=true, code=200}
     */
    @Operation(summary = "支付完成回调")
    @PostMapping("/callback")
    public Map<String, Object> callback(HttpServletRequest request) {
        JsonNode body = readNotifyBody(request);
        log.info("收到支付回调 contentType={} body={}", request.getContentType(), body);
        try {
            applicationService.handlePaymentNotify(body);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("支付回调处理失败 body={}", body, e);
            throw new BizException(ErrorCode.PAYMENT_NOTIFY_INVALID, e.getMessage());
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", true);
        resp.put("code", 200);
        resp.put("message", "ok");
        return resp;
    }

    /**
     * 读回调正文。线上工行是 {@code application/x-www-form-urlencoded}，
     * 不能只用 {@code @RequestBody} 收 JSON，否则会 415 进不了业务。
     *
     * @param request 原始 HTTP 请求
     * @return 已拆开 {@code biz_content} 的字段树，根上应有 {@code out_trade_no}
     */
    private JsonNode readNotifyBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (isForm(contentType)) {
            return unwrapBizContent(formToJson(request));
        }
        try {
            JsonNode json = objectMapper.readTree(request.getInputStream());
            if (json != null && !json.isNull() && !json.isMissingNode() && !json.isEmpty()) {
                return unwrapBizContent(json);
            }
        } catch (Exception e) {
            log.warn("支付回调 JSON 解析失败，改读表单 contentType={}", contentType, e);
        }
        return unwrapBizContent(formToJson(request));
    }

    /**
     * 工行把支付结果塞在表单字段 {@code biz_content}（一段 JSON 字符串）里。
     * 根节点只有 {@code api}、{@code sign}、{@code biz_content}，
     * 不拆开就找不到 {@code out_trade_no}，会误报 7007。
     *
     * @param body 表单或 JSON 根节点
     * @return 把内层字段合并到根上后的节点；解不开则原样返回
     */
    private JsonNode unwrapBizContent(JsonNode body) {
        if (body == null || !body.isObject()) {
            return body;
        }
        JsonNode nested = body.path("biz_content");
        if (nested.isMissingNode() || nested.isNull()) {
            nested = body.path("data");
        }
        JsonNode inner = null;
        if (nested.isTextual() && !nested.asText().isBlank()) {
            try {
                inner = objectMapper.readTree(nested.asText());
            } catch (Exception e) {
                log.warn("支付回调 biz_content 解析失败 raw={}", nested.asText());
            }
        } else if (nested.isObject()) {
            inner = nested;
        }
        if (inner == null || !inner.isObject()) {
            return body;
        }
        ObjectNode merged = ((ObjectNode) body).deepCopy();
        merged.setAll((ObjectNode) inner);
        return merged;
    }

    /**
     * @param contentType 请求 Content-Type，可能带 charset
     * @return 是否按表单字段读取
     */
    private static boolean isForm(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase();
        return normalized.contains("application/x-www-form-urlencoded")
                || normalized.contains("multipart/form-data");
    }

    /**
     * 把 form / query 参数收成 JSON 对象，方便后面统一对单。
     *
     * @param request 已由容器解析过的请求
     * @return 仅含非空首个取值的对象节点
     */
    private JsonNode formToJson(HttpServletRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()) {
                continue;
            }
            node.put(entry.getKey(), values[0]);
        }
        return node;
    }
}
