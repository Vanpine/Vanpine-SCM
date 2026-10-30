package cn.greensmartplanet.gsc.platform.client;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公司支付中台客户端。
 * <p>
 * 只管两段对外调用，不改尽调状态：
 * <ul>
 *   <li>下单：拿收款码，并把 GSC 回调地址登记为 notify_url</li>
 *   <li>回写 /paid：我们本地入账之后，再告诉中台这单已付</li>
 * </ul>
 * 尽调 {@code paid} 只由回调 {@code handlePaymentNotify} 写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorporatePaymentClient {

    /** 中台要求写死的开放接口 Key */
    private static final String API_KEY = "cna_open_payment_api_key_2026";

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${gsc.payment.api-base:https://api.adminpanel.corporate-advisory.cn}")
    private String apiBase;

    @Value("${gsc.payment.payment-main-id:2}")
    private int paymentMainId;

    @Value("${gsc.payment.notify-url}")
    private String notifyUrl;

    /**
     * 自定义金额下单。当前工行通道只收 CNY。
     * {@code notify_callback_type=1} 表示直连：付完由工行打我们的 notify_url，中台不改我们库。
     *
     * @param amountYuan 价目表金额（元）
     * @param title      收银台标题
     * @param bizRefId   业务单号，这里是申请 ID
     * @param remark     备注
     * @return data 节点，必须含 qrcode、out_trade_no
     */
    public JsonNode createOrder(BigDecimal amountYuan, String title, Long bizRefId, String remark) {
        if (notifyUrl == null || notifyUrl.isBlank()) {
            throw new BizException(ErrorCode.PAYMENT_CREATE_FAILED, "未配置支付回调地址 gsc.payment.notify-url");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_main_id", paymentMainId);
        body.put("amount", amountYuan);
        body.put("notify_callback_type", 1);
        body.put("notify_url", notifyUrl);
        body.put("title", title);
        body.put("biz_type", "dd_package");
        if (bizRefId != null) {
            body.put("biz_ref_id", bizRefId);
        }
        if (remark != null && !remark.isBlank()) {
            body.put("remark", remark);
        }
        JsonNode data = post("/api/pub/v1/payment/order", body, "创建支付单");
        if (text(data, "qrcode") == null || text(data, "out_trade_no") == null) {
            throw new BizException(ErrorCode.PAYMENT_CREATE_FAILED, "支付中台未返回收款码或商户订单号");
        }
        return data;
    }

    /**
     * 直连回写中台：{@code POST /api/pub/v1/payment/order/paid}。
     * 这是我们调中台，不是回调。失败只打日志，不影响本地已入账。
     * {@code order_id} 必须用工行银行订单号（201337…），不要传下单返回的 P2026…。
     *
     * @param outTradeNo 商户订单号
     * @param orderId    银行订单号
     * @param notify     工行回调字段，有则带上可选项（return_code、msg_id 等）
     */
    public void markPaid(String outTradeNo, String orderId, JsonNode notify) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("out_trade_no", outTradeNo);
        body.put("order_id", orderId);
        copyIfPresent(body, notify,
                "msg_id", "pay_time", "paid_at", "total_amt", "payment_amt",
                "return_code", "return_msg", "mer_id", "channel",
                "cust_id", "card_no", "bank_name", "attach");
        try {
            post("/api/pub/v1/payment/order/paid", body, "回写支付完成");
        } catch (Exception e) {
            log.warn("回写中台已支付失败 outTradeNo={}", outTradeNo, e);
        }
    }

    private static void copyIfPresent(Map<String, Object> body, JsonNode notify, String... fields) {
        if (notify == null || notify.isMissingNode() || notify.isNull()) {
            return;
        }
        for (String field : fields) {
            JsonNode node = notify.get(field);
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isNumber()) {
                body.put(field, node.numberValue());
            } else if (node.isTextual() && !node.asText().isBlank()) {
                body.put(field, node.asText());
            }
        }
    }

    private JsonNode post(String path, Map<String, Object> body, String action) {
        try {
            String raw = restClientBuilder.baseUrl(apiBase).build()
                    .post()
                    .uri(path)
                    .header("Authorization", "Bearer " + API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(raw);
            if (!json.path("status").asBoolean(false) || json.path("code").asInt() != 200) {
                throw new BizException(ErrorCode.PAYMENT_CREATE_FAILED,
                        action + "失败：" + json.path("message").asText("未知错误"));
            }
            return json.path("data");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}异常", action, e);
            throw new BizException(ErrorCode.PAYMENT_CREATE_FAILED, action + "异常：" + e.getMessage());
        }
    }

    public static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }
}
