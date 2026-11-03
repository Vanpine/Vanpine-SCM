package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI世界护照代理接口
 */
@Slf4j
@RestController
@RefreshScope
@RequestMapping("/api/passport")
@RequiredArgsConstructor
@Tag(name = "AI护照代理")
public class PassportController {

    private final ObjectMapper objectMapper;

    @Value("${gsc.passport.api-base}")
    private String passportApiBase;

    private final RestClient.Builder restClientBuilder;

    /** 通过 access_token 获取护照用户信息（代理转发到AI护照平台） */
    @Operation(summary = "获取护照用户信息")
    @PostMapping("/me")
    public R<Map<String, Object>> me(@RequestBody Map<String, String> body) {
        String accessToken = body.get("access_token");
        if (accessToken == null || accessToken.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "access_token 不能为空");
        }
        try {
            String responseBody = restClientBuilder.baseUrl(passportApiBase).build()
                    .get()
                    .uri("/api/oauth2/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode data = json.path("data");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("username", data.path("username").asText());
            result.put("nickname", data.path("nickname").asText());
            result.put("email", data.path("email").asText());
            result.put("phone", data.path("phone").asText());
            result.put("uuid", data.path("uuid").asText());
            JsonNode profile = data.path("profile");
            Map<String, Object> profileMap = new LinkedHashMap<>();
            profileMap.put("realName", profile.path("realName").asText());
            profileMap.put("idVerified", profile.path("idVerified").asBoolean(false));
            result.put("profile", profileMap);
            return R.ok(result);
        } catch (HttpClientErrorException e) {
            log.warn("护照 /me 请求失败, status={}", e.getStatusCode());
            throw new BizException(ErrorCode.UNAUTHORIZED, "AI护照身份校验失败，请重新登录");
        } catch (Exception e) {
            log.error("护照代理请求异常", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI护照服务暂时不可用，请稍后重试");
        }
    }
}
