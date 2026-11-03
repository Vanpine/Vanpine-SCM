package cn.greensmartplanet.gsc.ai.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 列表工具 pageSize 封顶测试。
 * <p>
 * pageSize 由模型生成，不封顶时模型传 500 就会把整页明细灌进上下文
 * （工具结果在 Python 侧还有字段投影，但源头闸门必须先有）。
 */
class AiToolPageSizeCapTest {

    private final AiToolServiceImpl service = new AiToolServiceImpl(
            null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("未传 pageSize 用默认 10")
    void defaultPageSize() {
        assertThat(service.resolvePageSize(Map.of())).isEqualTo(10);
    }

    @Test
    @DisplayName("模型传 500 被压到上限 20")
    void oversizedPageSizeCapped() {
        assertThat(service.resolvePageSize(Map.of("pageSize", 500)))
                .isEqualTo(AiToolServiceImpl.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("正常值原样保留")
    void normalPageSizeKept() {
        assertThat(service.resolvePageSize(Map.of("pageSize", 5))).isEqualTo(5);
    }

    @Test
    @DisplayName("0 或负数抬到 1，避免 LIMIT 0 / 负 offset")
    void nonPositivePageSizeClamped() {
        assertThat(service.resolvePageSize(Map.of("pageSize", 0))).isEqualTo(1);
        assertThat(service.resolvePageSize(Map.of("pageSize", -3))).isEqualTo(1);
    }
}
