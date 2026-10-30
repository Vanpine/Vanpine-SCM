package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.enums.MatchReasonStatus;
import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import cn.greensmartplanet.gsc.delivery.service.WeatherAdviceService;
import cn.greensmartplanet.gsc.delivery.support.WeatherAdviceCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气建议：Redis 共享结果，Python /api/delivery/weather-advice 写 DeepSeek。
 * Redis / 模型故障不抛给扫描主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherAdviceServiceImpl implements WeatherAdviceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public WeatherAdviceCache read(Long riskId) {
        if (riskId == null) {
            return null;
        }
        try {
            Object raw = redisTemplate.opsForValue().get(key(riskId));
            return coerce(raw);
        } catch (Exception e) {
            log.warn("读取天气建议缓存失败: riskId={}", riskId, e);
            return null;
        }
    }

    @Override
    public void generate(WeatherAdvicePayload payload) {
        if (payload == null || payload.getRiskId() == null) {
            return;
        }
        WeatherAdviceCache existing = read(payload.getRiskId());
        if (existing != null && MatchReasonStatus.READY.getCode().equals(existing.getStatus())
                && existing.getLines() != null && !existing.getLines().isEmpty()) {
            return;
        }
        String lockKey = key(payload.getRiskId()) + ":lock";
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, "1", Duration.ofSeconds(SystemConstants.WEATHER_ADVICE_LOCK_TTL));
            if (Boolean.FALSE.equals(locked)) {
                return;
            }
        } catch (Exception e) {
            log.warn("天气建议抢锁失败，继续生成: riskId={}", payload.getRiskId(), e);
        }
        try {
            write(payload.getRiskId(), MatchReasonStatus.GENERATING.getCode(), List.of());
            List<String> lines = callPython(payload);
            String status = MatchReasonStatus.READY.getCode();
            if (lines.isEmpty()) {
                lines = fallback(payload);
                status = MatchReasonStatus.FALLBACK.getCode();
            }
            write(payload.getRiskId(), status, lines);
        } catch (Exception e) {
            log.warn("天气建议生成失败，写入模板: riskId={}", payload.getRiskId(), e);
            write(payload.getRiskId(), MatchReasonStatus.FALLBACK.getCode(), fallback(payload));
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                log.warn("释放天气建议锁失败: riskId={}", payload.getRiskId(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> callPython(WeatherAdvicePayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("place", nz(payload.getPlace()));
        body.put("evidence", nz(payload.getEvidence()));
        body.put("summary", nz(payload.getSummary()));
        body.put("level", nz(payload.getLevel()));
        body.put("delivery_no", nz(payload.getDeliveryNo()));
        body.put("ship_company", nz(payload.getShipCompany()));
        body.put("delivery_place", nz(payload.getDeliveryPlace()));
        body.put("eta", nz(payload.getEta()));
        body.put("promised_date", nz(payload.getPromisedDate()));
        body.put("impact_qty", payload.getImpactQty() == null ? "" : String.valueOf(payload.getImpactQty()));
        Map<String, Object> response = aiRestClient.post()
                .uri("/api/delivery/weather-advice")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            return List.of();
        }
        Object raw = response.get("lines");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                lines.add(item.toString().trim());
            }
        }
        return lines;
    }

    private void write(Long riskId, String status, List<String> lines) {
        WeatherAdviceCache cache = new WeatherAdviceCache(status, new ArrayList<>(lines));
        redisTemplate.opsForValue().set(key(riskId), cache,
                Duration.ofSeconds(SystemConstants.WEATHER_ADVICE_CACHE_TTL));
    }

    private WeatherAdviceCache coerce(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof WeatherAdviceCache cache) {
            return cache;
        }
        try {
            return objectMapper.convertValue(raw, WeatherAdviceCache.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static List<String> fallback(WeatherAdvicePayload payload) {
        String place = nz(payload.getPlace()).isEmpty() ? "当前节点" : payload.getPlace();
        String evidence = !nz(payload.getEvidence()).isEmpty() ? payload.getEvidence()
                : (!nz(payload.getSummary()).isEmpty() ? payload.getSummary() : "天气异常");
        return List.of(
                "关注" + place + "未来 3 日天气：" + evidence + "，作业前再确认一次能否进出港或上路。",
                "联系承运商确认该节点是否停航、封路或限行，不要按原计划盲发。",
                "对照考核交期评估改期或绕行；交期紧可考虑拆出紧急量改空运。",
                "通知买卖双方天气风险已触发，同步包装加固与到货预期，避免到厂无准备。"
        );
    }

    private static String key(Long riskId) {
        return SystemConstants.WEATHER_ADVICE_CACHE_PREFIX + riskId;
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }
}
