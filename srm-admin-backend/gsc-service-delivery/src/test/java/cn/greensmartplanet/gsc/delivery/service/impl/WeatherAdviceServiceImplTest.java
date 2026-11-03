package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherAdviceServiceImplTest {

    @Test
    void fallback_mentionsPlaceAndAction() {
        WeatherAdvicePayload payload = WeatherAdvicePayload.builder()
                .place("杭州西湖")
                .evidence("杭州 · 雷暴 · 8级风")
                .build();
        assertThat(WeatherAdviceServiceImpl.fallback(payload))
                .hasSize(4)
                .allMatch(line -> !line.isBlank())
                .anyMatch(line -> line.contains("杭州西湖"));
    }
}
