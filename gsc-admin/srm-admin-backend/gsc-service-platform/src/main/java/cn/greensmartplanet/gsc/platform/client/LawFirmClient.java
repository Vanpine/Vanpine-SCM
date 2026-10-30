package cn.greensmartplanet.gsc.platform.client;

import cn.greensmartplanet.gsc.platform.dto.FirmInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 事务所查询客户端 — 调用公司事务所 API 获取事务所列表
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LawFirmClient {

    private final ObjectMapper objectMapper;

    @Value("${gsc.law-firm.api-base}")
    private String apiBase;

    private final RestClient.Builder restClientBuilder;

    /**
     * 查询事务所列表，按关键字过滤
     *
     * @param keyword 搜索关键字（匹配名称或联系人），为空则返回全部
     * @return 事务所信息列表
     */
    public List<FirmInfo> search(String keyword) {
        try {
            // 1. 调公司事务所 API
            String responseBody = restClientBuilder.baseUrl(apiBase).build()
                    .get()
                    .uri("/api/v1/public/cnaCompany")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            // 2. 解析响应（公司 API 成功 code=0）
            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.path("code").asInt();
            if (code != 0) {
                log.warn("事务所查询接口返回错误, code={}, message={}", code, json.path("message").asText());
                return Collections.emptyList();
            }

            // 3. 解析 list 并转换
            JsonNode list = json.path("data").path("list");
            List<FirmInfo> firms = new ArrayList<>();
            for (JsonNode node : list) {
                FirmInfo info = parseFirm(node);
                if (info != null) {
                    firms.add(info);
                }
            }

            // 4. 客户端关键字过滤（API 可能不支持服务端搜索）
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                firms = firms.stream()
                        .filter(f -> (f.getFirmName() != null && f.getFirmName().toLowerCase().contains(kw))
                                || (f.getContactName() != null && f.getContactName().toLowerCase().contains(kw)))
                        .toList();
            }

            log.info("事务所查询成功, keyword={}, total={}", keyword, firms.size());
            return firms;

        } catch (Exception e) {
            log.error("事务所查询异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析单条事务所记录（EAV 模式 → 扁平 DTO）
     */
    private FirmInfo parseFirm(JsonNode node) {
        FirmInfo info = new FirmInfo();

        // 顶层字段
        info.setFirmId(node.path("id").asLong());
        info.setFirmName(node.path("name").asText());
        info.setStatus(node.path("status").asInt());

        // 解析 info[] EAV 数组，只取 type=1 的实际字段，按 field_key 分组取最新 version
        JsonNode infoArray = node.path("info");
        Map<String, String> fields = new LinkedHashMap<>();
        if (infoArray.isArray()) {
            for (JsonNode item : infoArray) {
                if (item.path("type").asInt() != 1) continue; // 跳过 type=0 分组标题
                String key = item.path("field_key").asText();
                String value = item.path("field_value").asText();
                // 同一 field_key 多次出现（版本快照），后面的覆盖前面的（取最新）
                if (value != null && !value.isBlank()) {
                    fields.put(key, value);
                }
            }
        }

        // 映射到 DTO
        info.setContactName(fields.get("contact_name"));
        info.setContactEmail(fields.get("email"));
        info.setContactPhone(fields.get("mobile"));
        info.setProvince(fields.get("level1"));
        info.setCity(fields.get("level2"));
        info.setDistrict(fields.get("level3"));
        info.setAddress(fields.get("address"));
        info.setServiceScope(fields.get("service"));
        info.setServiceSector(fields.get("service_sector"));

        return info;
    }
}
