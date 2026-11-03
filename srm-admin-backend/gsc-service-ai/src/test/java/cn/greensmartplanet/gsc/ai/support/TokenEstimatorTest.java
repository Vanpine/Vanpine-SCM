package cn.greensmartplanet.gsc.ai.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Token 计量的跨语言一致性。
 * <p>
 * 期望值是用 Python 侧 {@code app/services/tokenizer.py} 的 {@code count_tokens}
 * 实际跑出来的。两边口径必须一致——Java 算历史窗口预算、Python 判块大小，
 * 算出来不是一个数阈值就没法对齐。改任一侧的权重/正则都要同步改另一侧并更新本用例。
 */
class TokenEstimatorTest {

    @Test
    @DisplayName("与 Python count_tokens 同源：同一文本两边算出同一个数")
    void matchesPythonCountTokens() {
        assertThat(TokenEstimator.count("共 3 份合同。")).isEqualTo(9);
        assertThat(TokenEstimator.count("查我的合同")).isEqualTo(6);
        assertThat(TokenEstimator.count("上轮 3 条中第 2 条是 **pcb采集**：")).isEqualTo(21);
        assertThat(TokenEstimator.count("HT-2024-001 号合同已签署，金额 1,234,567.89 元。")).isEqualTo(21);
        assertThat(TokenEstimator.count("hello world foo bar")).isEqualTo(8);
        assertThat(TokenEstimator.count("深圳华创芯科技有限公司。智能控制模组、物联网通信模块")).isEqualTo(29);
        assertThat(TokenEstimator.count("字".repeat(500))).isEqualTo(551);
    }

    @Test
    @DisplayName("空文本返回 0（不返回 1）")
    void emptyReturnsZero() {
        assertThat(TokenEstimator.count(null)).isZero();
        assertThat(TokenEstimator.count("")).isZero();
    }

    @Test
    @DisplayName("英文长词不再被字符数高估")
    void englishWordNotOverestimated() {
        // "HT-2024-001" 11 字符，按 len() 会算 11+；加权后整词约 1.3 token
        assertThat(TokenEstimator.count("HT-2024-001")).isLessThan(11);
    }
}
