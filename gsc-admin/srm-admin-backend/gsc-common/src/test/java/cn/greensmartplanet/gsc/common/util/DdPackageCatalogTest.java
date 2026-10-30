package cn.greensmartplanet.gsc.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 尽调套餐价目表：锁住「独立定价、不开通即不可付、按国家默认币种」。
 */
class DdPackageCatalogTest {

    @Test
    @DisplayName("已开通套餐+币种返回独立标价，不是汇率折算")
    void findPrice_supportedPair_returnsCatalogAmount() {
        assertEquals(14900L, DdPackageCatalog.findPrice("basic", "USD").orElseThrow());
        assertEquals(13900L, DdPackageCatalog.findPrice("basic", "eur").orElseThrow());
        assertEquals(1L, DdPackageCatalog.findPrice("BASIC", "CNY").orElseThrow());
        assertEquals(299900L, DdPackageCatalog.findPrice("standard", "CNY").orElseThrow());
    }

    @Test
    @DisplayName("未开通套餐或币种查不到价")
    void findPrice_unknown_empty() {
        assertTrue(DdPackageCatalog.findPrice("vip", "USD").isEmpty());
        assertTrue(DdPackageCatalog.findPrice("basic", "JPY").isEmpty());
        assertTrue(DdPackageCatalog.findPrice(null, "USD").isEmpty());
        assertTrue(DdPackageCatalog.findPrice("basic", null).isEmpty());
    }

    @Test
    @DisplayName("中国大陆默认 CNY")
    void defaultCurrency_china_cny() {
        assertEquals("CNY", DdPackageCatalog.defaultCurrency("中国"));
        assertEquals("CNY", DdPackageCatalog.defaultCurrency("China"));
        assertEquals("CNY", DdPackageCatalog.defaultCurrency("CN"));
        assertEquals("CNY", DdPackageCatalog.defaultCurrency("People's Republic of China"));
        assertEquals("CNY", DdPackageCatalog.defaultCurrency("中国/CN"));
        assertEquals("CNY", DdPackageCatalog.defaultCurrency("中國"));
    }

    @Test
    @DisplayName("欧元区默认 EUR")
    void defaultCurrency_eurozone_eur() {
        assertEquals("EUR", DdPackageCatalog.defaultCurrency("德国"));
        assertEquals("EUR", DdPackageCatalog.defaultCurrency("Germany"));
        assertEquals("EUR", DdPackageCatalog.defaultCurrency("FR"));
        assertEquals("EUR", DdPackageCatalog.defaultCurrency("欧元区"));
    }

    @Test
    @DisplayName("其余国家（含港澳台）默认 USD，空值也是 USD")
    void defaultCurrency_fallback_usd() {
        assertEquals("USD", DdPackageCatalog.defaultCurrency(null));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("  "));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("美国"));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("日本"));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("香港"));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("Hong Kong"));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("台湾"));
        assertEquals("USD", DdPackageCatalog.defaultCurrency("英国"));
    }
}
