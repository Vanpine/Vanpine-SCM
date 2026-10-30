package cn.greensmartplanet.gsc.common.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 金额、币种和报表期间组合校验。
 */
public final class CurrencyAmountValidator {

    private static final Set<String> ISO_4217_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    private CurrencyAmountValidator() {
    }

    public static boolean isSupportedCurrency(String code) {
        return code != null && ISO_4217_CODES.contains(code.toUpperCase(Locale.ROOT));
    }

    public static Optional<String> getAmountError(String fieldLabel, BigDecimal amount, String currency,
                                                  LocalDate periodStart, LocalDate periodEnd) {
        if (amount == null && isBlank(currency) && periodStart == null && periodEnd == null) {
            return Optional.empty();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(fieldLabel + "金额必须大于 0");
        }
        if (isBlank(currency) || !ISO_4217_CODES.contains(currency)) {
            return Optional.of(fieldLabel + "币种必须是 ISO 4217 编码");
        }
        if (periodStart == null || periodEnd == null) {
            return Optional.of(fieldLabel + "报表期间不能为空");
        }
        if (periodStart.isAfter(periodEnd)) {
            return Optional.of(fieldLabel + "报表期间开始日期不能晚于结束日期");
        }
        return Optional.empty();
    }

    public static boolean isComplete(BigDecimal amount, String currency) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0 && !isBlank(currency);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
