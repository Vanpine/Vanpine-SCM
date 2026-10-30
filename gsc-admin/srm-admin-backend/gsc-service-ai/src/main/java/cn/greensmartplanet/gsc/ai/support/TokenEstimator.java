package cn.greensmartplanet.gsc.ai.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token 计量工具 — 按字符类别加权估算 token 数，替代 {@code String.length()} 字符计数。
 *
 * <p>与 Python 侧 {@code app/services/tokenizer.py} 的 {@code count_tokens} 同源同参，
 * 保证 Java 算历史窗口预算、Python 判块大小时口径一致——否则同一段文本两边算出不同的数，
 * 阈值就没法对齐。
 *
 * <p>为什么不用 len()：中文合同/规格书里中英数混排，纯字符计数会让块大小漂移——
 * 汉字 1 字 ≈ 1 token；英文/数字词（如 {@code HT-2024-001}）整段 ≈ 1.3 token/词，
 * 按 len() 会严重高估；标点/空白按 0.5 折。
 *
 * <p>不引入 tiktoken / transformers：本环境无 HF 缓存、无 GPU，重型 tokenizer 反而会静默回退。
 * 启发式 ±15% 误差对「窗口/块上限」判断足够。
 *
 * @author gsc
 */
public final class TokenEstimator {

    /** 汉字 / 全角标点 / 日韩字符：视为 1 token/字 */
    private static final Pattern CJK = Pattern.compile(
            "[一-鿿　-〿＀-￯぀-ヿ]");
    /** 英文数字词组（含连字符/下划线/斜杠/点，保住 HT-2024-001 这类整词） */
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9][A-Za-z0-9\\-_/.]*");

    private static final double CJK_WEIGHT = 1.0;
    private static final double WORD_WEIGHT = 1.3;
    private static final double OTHER_WEIGHT = 0.5;
    private static final double SAFETY = 1.1;

    private TokenEstimator() {
    }

    /**
     * 估算文本 token 数（向下取整后 +1，与 Python 侧一致）。
     *
     * @param text 待计量文本
     * @return 估算 token 数；空文本返回 0
     */
    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = countMatches(CJK, text);
        int words = 0;
        int wordChars = 0;
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            words++;
            wordChars += m.group().length();
        }
        // 既不是 CJK 也不属于任何词组的字符（标点/空白/符号）
        int other = Math.max(text.length() - cjk - wordChars, 0);
        double raw = cjk * CJK_WEIGHT + words * WORD_WEIGHT + other * OTHER_WEIGHT;
        return (int) (raw * SAFETY) + 1;
    }

    private static int countMatches(Pattern pattern, String text) {
        int n = 0;
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }
}
