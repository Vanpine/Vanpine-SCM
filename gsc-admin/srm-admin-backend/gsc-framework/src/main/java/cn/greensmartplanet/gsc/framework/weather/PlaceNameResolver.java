package cn.greensmartplanet.gsc.framework.weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物流自由文本 → 可查天气的城市名。
 * <p>
 * Open-Meteo 地名库只认城市/乡镇，不认「杭州西湖」「广州天河智慧城」。
 * 本类只做字符串清洗，不打外部接口：先抽城市，再交给 {@link WeatherClient#geocode}。
 * <p>
 * 通名（西湖、天河、南山）不进词表，避免指到同名乡镇。
 */
public final class PlaceNameResolver {

    private static final Pattern LEADING_PROVINCE = Pattern.compile(
            "^(?:[\u4e00-\u9fa5]{2,8}(?:维吾尔自治区|壮族自治区|回族自治区|自治区|省))");
    private static final Pattern PUNCT = Pattern.compile("[\\s,，。.;；:：/\\\\|、_\\-—()（）\\[\\]【】]+");

    /**
     * 单独出现时不能当城市：景区、区县、园区通名，Open-Meteo 会落到别的省。
     */
    private static final Set<String> AMBIGUOUS = Set.of(
            "西湖", "天河", "南山", "海淀", "浦东", "黄埔", "江北", "城东", "新城",
            "高新", "开发", "工业", "智慧", "园区", "保税", "港口", "码头", "机场",
            "车站", "仓库", "物流", "朝阳", "通州");

    /** 字数从长到短，保证「呼和浩特」先于更短词 */
    private static final List<String> CITIES = loadCities();

    private PlaceNameResolver() {
    }

    /**
     * 从详细地点抽出城市。抽不到返回 null（宁可不查，也不指错城）。
     *
     * @param raw 物流节点/交货地原文
     * @return 词表中的城市名，如 杭州 / Guangzhou
     */
    public static String extractCity(String raw) {
        String text = normalize(raw);
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("中国")) {
            text = text.substring(2);
        }
        String afterProvince = stripLeadingProvince(text);
        String fromRemainder = matchIn(afterProvince);
        if (fromRemainder != null) {
            return fromRemainder;
        }
        return afterProvince.equals(text) ? null : matchIn(text);
    }

    /**
     * 词表未命中时，从右往左削，给 Open-Meteo 再试。
     * 通名（西湖/天河）不会出现在列表里。
     */
    public static List<String> fallbackQueries(String raw) {
        String text = normalize(raw);
        if (text.startsWith("中国")) {
            text = text.substring(2);
        }
        text = stripLeadingProvince(text);
        List<String> out = new ArrayList<>();
        if (text.isEmpty() || extractCity(raw) != null) {
            return out;
        }
        if (containsCjk(text)) {
            for (int end = text.length() - 1; end >= 2 && out.size() < 4; end--) {
                String slice = text.substring(0, end);
                if (!AMBIGUOUS.contains(slice)) {
                    out.add(slice);
                }
            }
            return out;
        }
        String[] words = text.split(" ");
        for (int keep = words.length - 1; keep >= 1 && out.size() < 4; keep--) {
            out.add(String.join(" ", java.util.Arrays.copyOf(words, keep)));
        }
        return out;
    }

    public static boolean isAmbiguousAtom(String name) {
        return name != null && AMBIGUOUS.contains(name.trim());
    }

    private static String matchIn(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String prefix = longestPrefix(text);
        String contained = longestContained(text);
        // 吉林长春：前缀是吉林，但后面紧跟另一座城市，用后面的
        if (prefix != null && contained != null && !prefix.equals(contained)) {
            return contained;
        }
        return prefix != null ? prefix : contained;
    }

    private static String longestPrefix(String text) {
        String compact = text.replace(" ", "");
        String lower = text.toLowerCase(Locale.ROOT);
        for (String city : CITIES) {
            if (containsCjk(city)) {
                if (compact.startsWith(city) || compact.startsWith(city + "市")) {
                    return city;
                }
            } else if (lower.startsWith(city.toLowerCase(Locale.ROOT))) {
                return city;
            }
        }
        return null;
    }

    /**
     * 正文中出现的城市：更长优先；若短名后面紧跟另一个城市（吉林长春），丢掉短名。
     */
    private static String longestContained(String text) {
        String compact = text.replace(" ", "");
        String lower = text.toLowerCase(Locale.ROOT);
        record Hit(String city, int idx, int len) {
        }
        List<Hit> hits = new ArrayList<>();
        for (String city : CITIES) {
            int idx = containsCjk(city)
                    ? compact.indexOf(city)
                    : lower.indexOf(city.toLowerCase(Locale.ROOT));
            if (idx < 0) {
                continue;
            }
            int after = idx + city.length();
            String src = containsCjk(city) ? compact : text;
            if (after < src.length() && (src.charAt(after) == '省' || src.startsWith("自治区", after))) {
                continue;
            }
            hits.add(new Hit(city, idx, city.length()));
        }
        if (hits.isEmpty()) {
            return null;
        }
        Set<String> dropped = new LinkedHashSet<>();
        for (Hit a : hits) {
            for (Hit b : hits) {
                if (a != b && a.idx + a.len == b.idx) {
                    dropped.add(a.city);
                }
            }
        }
        Hit best = null;
        for (Hit hit : hits) {
            if (dropped.contains(hit.city)) {
                continue;
            }
            if (best == null || hit.len > best.len || (hit.len == best.len && hit.idx < best.idx)) {
                best = hit;
            }
        }
        return best == null ? null : best.city;
    }

    private static String stripLeadingProvince(String text) {
        Matcher matcher = LEADING_PROVINCE.matcher(text);
        if (matcher.find()) {
            return text.substring(matcher.end());
        }
        return text;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return PUNCT.matcher(raw.trim()).replaceAll(" ").trim().replaceAll(" +", " ");
    }

    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x4E00) {
                return true;
            }
        }
        return false;
    }

    private static List<String> loadCities() {
        InputStream in = PlaceNameResolver.class.getResourceAsStream("/weather/city-gazetteer.txt");
        if (in == null) {
            throw new IllegalStateException("缺少词表 classpath:/weather/city-gazetteer.txt");
        }
        Set<String> unique = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                if (!AMBIGUOUS.contains(name)) {
                    unique.add(name);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取城市词表失败", e);
        }
        List<String> list = new ArrayList<>(unique);
        list.sort(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s));
        return List.copyOf(list);
    }
}
