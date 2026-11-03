package cn.greensmartplanet.gsc.framework.auth;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AI 世界护照身份校验客户端
 * <p>
 * 微服务拆分：由 GscFrameworkAutoConfiguration 注册（配置 gsc.passport.* 才生效）。
 *
 * @author gsc
 */
@Slf4j
@RequiredArgsConstructor
public class PassportClient {

    private final ObjectMapper objectMapper;

    @Value("${gsc.passport.client-id}")
    private String clientId;

    @Value("${gsc.passport.client-secret}")
    private String clientSecret;

    @Value("${gsc.passport.api-base}")
    private String apiBase;

    private final RestClient.Builder restClientBuilder;

    /**
     * 通过 UUID 校验护照用户身份
     */
    public PassportUserInfo verifyUser(String uuid) {
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "PASSPORT_CLIENT_SECRET 未配置");
        }

        try {
            String credentials = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            String body = "uuid=" + URLEncoder.encode(uuid, StandardCharsets.UTF_8);

            String responseBody = restClientBuilder.baseUrl(apiBase).build()
                    .post()
                    .uri("/api/oauth2/userinfo/by-uuid")
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(responseBody);

            int code = json.path("code").asInt();
            if (code == 401) {
                throw new BizException(ErrorCode.PASSPORT_VERIFY_FAILED, "客户端凭证无效，请检查 client_id 和 client_secret");
            }
            if (code == 404) {
                throw new BizException(ErrorCode.PASSPORT_USER_NOT_FOUND, "用户不存在，请确认已通过AI世界护照注册");
            }
            if (!json.path("status").asBoolean(false)) {
                throw new BizException(ErrorCode.PASSPORT_VERIFY_FAILED, json.path("message").asText("用户身份校验失败"));
            }

            JsonNode data = json.path("data");
            return PassportUserInfo.builder()
                    .uuid(uuid)
                    .username(data.path("username").asText())
                    .email(data.path("email").asText())
                    .phone(data.path("phone").asText())
                    .realName(data.path("profile").path("realName").asText())
                    .idVerified(data.path("profile").path("idVerified").asBoolean(false))
                    .build();

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("护照身份校验失败", e);
            throw new BizException(ErrorCode.PASSPORT_VERIFY_FAILED, "护照身份校验失败: " + e.getMessage());
        }
    }
}
