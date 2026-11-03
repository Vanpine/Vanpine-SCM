package cn.greensmartplanet.gsc.framework.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 详细地点抽城市：词表命中、省名前缀、通名拒绝。
 */
class PlaceNameResolverTest {

    @Test
    void detailedPlace_extractsCity() {
        assertThat(PlaceNameResolver.extractCity("杭州西湖")).isEqualTo("杭州");
        assertThat(PlaceNameResolver.extractCity("广州天河智慧城")).isEqualTo("广州");
        assertThat(PlaceNameResolver.extractCity("杭州市西湖区")).isEqualTo("杭州");
        assertThat(PlaceNameResolver.extractCity("中国杭州西湖")).isEqualTo("杭州");
    }

    @Test
    void provincePrefix_thenCity() {
        assertThat(PlaceNameResolver.extractCity("浙江省杭州市西湖区")).isEqualTo("杭州");
        assertThat(PlaceNameResolver.extractCity("吉林省长春市")).isEqualTo("长春");
        assertThat(PlaceNameResolver.extractCity("黑龙江哈尔滨")).isEqualTo("哈尔滨");
        assertThat(PlaceNameResolver.extractCity("吉林长春")).isEqualTo("长春");
    }

    @Test
    void englishPort_extractsCity() {
        assertThat(PlaceNameResolver.extractCity("Los Angeles Port")).isEqualTo("Los Angeles");
        assertThat(PlaceNameResolver.extractCity("Guangzhou Tianhe Smart City")).isEqualTo("Guangzhou");
    }

    @Test
    void cityItself_unchanged() {
        assertThat(PlaceNameResolver.extractCity("杭州")).isEqualTo("杭州");
        assertThat(PlaceNameResolver.extractCity("上海")).isEqualTo("上海");
    }

    @Test
    void ambiguousAlone_rejected() {
        assertThat(PlaceNameResolver.extractCity("西湖")).isNull();
        assertThat(PlaceNameResolver.extractCity("天河")).isNull();
        assertThat(PlaceNameResolver.isAmbiguousAtom("西湖")).isTrue();
    }
}
