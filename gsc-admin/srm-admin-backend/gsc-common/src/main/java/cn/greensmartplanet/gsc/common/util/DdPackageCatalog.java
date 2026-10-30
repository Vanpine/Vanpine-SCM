package cn.greensmartplanet.gsc.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 尽调套餐价目表 — 支付金额的唯一权威。
 * <p>
 * 企业常见作法：平台开通有限结算币种，各币种独立标价，不做实时汇率折算。
 * 用户只能在已开通币种中选择；默认币种按企业所在国家推断。
 */
public final class DdPackageCatalog {

    public static final String USD = "USD";
    public static final String EUR = "EUR";
    public static final String CNY = "CNY";

    /** 已开通支付币种，顺序即前端下拉顺序 */
    public static final List<String> CURRENCIES = List.of(USD, EUR, CNY);

    public static final Set<String> PACKAGE_TYPES = Set.of("basic", "standard", "premium");

    /**
     * 套餐 → 币种 → 最小货币单位金额。
     * ⚠️ 各币种价格为占位值，待运营确认后替换。
     */
    private static final Map<String, Map<String, Long>> PRICES;

    private static final Set<String> CNY_COUNTRIES = Set.of(
            "cn", "chn", "china", "prc", "中国", "中华人民共和国", "中国大陆",
            "mainlandchina", "peoplesrepublicofchina"
    );

    private static final Set<String> EUR_COUNTRIES = Set.of(
            "at", "aut", "austria", "奥地利",
            "be", "bel", "belgium", "比利时",
            "cy", "cyp", "cyprus", "塞浦路斯",
            "de", "deu", "germany", "德国",
            "ee", "est", "estonia", "爱沙尼亚",
            "es", "esp", "spain", "西班牙",
            "fi", "fin", "finland", "芬兰",
            "fr", "fra", "france", "法国",
            "gr", "grc", "greece", "希腊",
            "hr", "hrv", "croatia", "克罗地亚",
            "ie", "irl", "ireland", "爱尔兰",
            "it", "ita", "italy", "意大利",
            "lt", "ltu", "lithuania", "立陶宛",
            "lu", "lux", "luxembourg", "卢森堡",
            "lv", "lva", "latvia", "拉脱维亚",
            "mt", "mlt", "malta", "马耳他",
            "nl", "nld", "netherlands", "荷兰",
            "pt", "prt", "portugal", "葡萄牙",
            "si", "svn", "slovenia", "斯洛文尼亚",
            "sk", "svk", "slovakia", "斯洛伐克",
            "eurozone", "欧元区"
    );

    static {
        Map<String, Map<String, Long>> prices = new LinkedHashMap<>();
        prices.put("basic", priceRow(14900L, 13900L, 1L));
        prices.put("standard", priceRow(44900L, 41900L, 299900L));
        prices.put("premium", priceRow(89900L, 83900L, 599900L));
        PRICES = Collections.unmodifiableMap(prices);
    }

    private DdPackageCatalog() {
    }

    /** 价目表快照（套餐 → 币种 → 最小货币单位），供前端展示，不可变 */
    public static Map<String, Map<String, Long>> prices() {
        return PRICES;
    }

    public static boolean isSupportedPackage(String packageType) {
        return packageType != null && PACKAGE_TYPES.contains(normalizePackage(packageType));
    }

    public static boolean isSupportedCurrency(String currency) {
        return currency != null && CURRENCIES.contains(normalizeCurrency(currency));
    }

    /**
     * 查询套餐在指定币种下的定价。
     *
     * @param packageType 套餐类型 basic/standard/premium
     * @param currency    ISO 4217 支付币种
     * @return 最小货币单位金额；套餐或币种未开通时为空
     */
    public static Optional<Long> findPrice(String packageType, String currency) {
        if (!isSupportedPackage(packageType) || !isSupportedCurrency(currency)) {
            return Optional.empty();
        }
        Long amount = PRICES.get(normalizePackage(packageType)).get(normalizeCurrency(currency));
        return Optional.ofNullable(amount);
    }

    /**
     * 按企业所在国家推断默认支付币种。
     * 中国大陆 → CNY，欧元区 → EUR，其余（含港澳台）→ USD。
     * 国家为自由文本，兼容中文名 / 英文名 / ISO 代码。
     *
     * @param country 企业所在国家/地区，空则 USD
     */
    public static String defaultCurrency(String country) {
        String key = normalizeCountry(country);
        if (key.isEmpty()) {
            return USD;
        }
        // 港澳台优先：避免被「中国」子串误判成 CNY
        if (containsAny(key, "香港", "hongkong", "台湾", "taiwan", "澳门", "macau", "macao")) {
            return USD;
        }
        if (CNY_COUNTRIES.contains(key) || key.contains("中国") || key.contains("中國") || key.contains("china")) {
            return CNY;
        }
        if (EUR_COUNTRIES.contains(key) || key.contains("欧元区")) {
            return EUR;
        }
        return USD;
    }

    private static boolean containsAny(String key, String... tokens) {
        for (String token : tokens) {
            if (key.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Long> priceRow(long usd, long eur, long cny) {
        Map<String, Long> row = new LinkedHashMap<>();
        row.put(USD, usd);
        row.put(EUR, eur);
        row.put(CNY, cny);
        return Collections.unmodifiableMap(row);
    }

    private static String normalizePackage(String packageType) {
        return packageType.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "";
        }
        return country.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }
}
