package cn.greensmartplanet.gsc.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * 解析失败原因码 → 用户可读提示。
 * <p>
 * Python /api/documents/parse 失败时返回
 * {@code {"detail":{"code":"SCANNED_NEEDS_OCR","message":"..."}}}，
 * 这里解析该结构并按原因码给出可操作文案（如「上传 OCR 版本」），
 * 同时在末尾保留原因码，便于日志排查。
 * <p>
 * 原因码契约与 srm-ai-service app/docparse/types.py 保持一致。
 */
public final class ParseErrorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 原因码 → 用户提示（新增原因码时两端同步）。 */
    private static final Map<String, String> MESSAGES = Map.of(
            "SCANNED_NEEDS_OCR", "文档疑似扫描件，未识别到可用文本，请上传 OCR 版本或可复制文本的 PDF",
            "OCR_FAILED", "文档为扫描件，OCR 识别失败，请稍后重试或上传可复制文本的 PDF",
            "OCR_PAGE_LIMIT", "扫描页过多，超过单次 OCR 上限，请分册上传",
            "EMPTY_TEXT", "文档中未提取到有效文本，请检查文件内容",
            "ENCRYPTED", "文档已加密，无法解析，请解除密码后重试",
            "UNSUPPORTED_TYPE", "不支持的文档类型，请上传 txt / md / pdf / docx / xlsx 文件",
            "PARSE_ERROR", "文档解析失败，请检查文件格式或内容"
    );

    private ParseErrorSupport() {
    }

    /**
     * 把解析调用异常翻译成可写入 ai_documents.error_message 的提示。
     *
     * @param error 调用 Python 解析接口抛出的异常
     * @return 用户可读提示；能识别原因码时追加「（CODE）」
     */
    public static String toErrorMessage(Throwable error) {
        // 1. 只有 HTTP 响应异常才带 Python 返回的结构化 detail
        String code = null;
        String detailMessage = null;
        if (error instanceof RestClientResponseException responseException) {
            JsonNode detail = readDetail(responseException.getResponseBodyAsString());
            if (detail != null && detail.isObject()) {
                code = text(detail, "code");
                detailMessage = text(detail, "message");
            } else if (detail != null && detail.isTextual()) {
                detailMessage = detail.asText();
            }
        }

        // 2. 已知原因码优先用统一文案；未知码回退后端原文，不吞信息
        String mapped = code == null ? null : MESSAGES.get(code);
        String base;
        if (mapped != null) {
            base = mapped;
        } else if (detailMessage != null && !detailMessage.isBlank()) {
            base = detailMessage;
        } else {
            base = MESSAGES.get("PARSE_ERROR");
        }

        // 3. 附加原因码，便于前端展示与日志排查
        return code == null ? base : base + "（" + code + "）";
    }

    private static JsonNode readDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body).path("detail");
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
