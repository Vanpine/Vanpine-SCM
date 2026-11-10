package cn.greensmartplanet.gsc.ai.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 解析失败原因码翻译测试。
 * <p>
 * 锁定：Python 返回 detail={code,message} 时，用户看到的必须是可操作的中文提示，
 * 并保留原因码便于排查；非结构化异常回退到统一兜底文案。
 */
class ParseErrorSupportTest {

    private static HttpClientErrorException unprocessable(String body) {
        return HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity",
                HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("扫描件：给出 OCR 提示并保留原因码")
    void scannedPdfGivesActionableMessage() {
        String msg = ParseErrorSupport.toErrorMessage(unprocessable(
                "{\"detail\":{\"code\":\"SCANNED_NEEDS_OCR\",\"message\":\"文档疑似扫描件\"}}"));

        assertThat(msg).contains("扫描").contains("SCANNED_NEEDS_OCR");
    }

    @Test
    @DisplayName("空文本 / 加密：按原因码返回对应提示")
    void knownCodesMapped() {
        assertThat(ParseErrorSupport.toErrorMessage(unprocessable(
                "{\"detail\":{\"code\":\"EMPTY_TEXT\",\"message\":\"x\"}}")))
                .contains("EMPTY_TEXT");
        assertThat(ParseErrorSupport.toErrorMessage(unprocessable(
                "{\"detail\":{\"code\":\"ENCRYPTED\",\"message\":\"x\"}}")))
                .contains("加密").contains("ENCRYPTED");
    }

    @Test
    @DisplayName("detail 是字符串：原样展示")
    void stringDetailUsed() {
        String msg = ParseErrorSupport.toErrorMessage(unprocessable(
                "{\"detail\":\"boom from python\"}"));
        assertThat(msg).contains("boom from python");
    }

    @Test
    @DisplayName("未知原因码：展示后端原文，不吞信息")
    void unknownCodeUsesRawMessage() {
        String msg = ParseErrorSupport.toErrorMessage(unprocessable(
                "{\"detail\":{\"code\":\"WEIRD\",\"message\":\"自定义失败原因\"}}"));
        assertThat(msg).contains("自定义失败原因");
    }

    @Test
    @DisplayName("非 HTTP 异常：回退统一兜底文案")
    void nonHttpExceptionFallsBack() {
        assertThat(ParseErrorSupport.toErrorMessage(new RuntimeException("socket closed")))
                .contains("解析失败");
    }
}
