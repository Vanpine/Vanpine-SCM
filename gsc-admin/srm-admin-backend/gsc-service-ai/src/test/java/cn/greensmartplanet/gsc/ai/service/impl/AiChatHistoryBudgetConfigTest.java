package cn.greensmartplanet.gsc.ai.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归：history-token-budget 必须带默认值。
 * <p>
 * 原实现是 {@code @Value("${gsc.ai.context.history-token-budget}")} 无兜底，且仓库
 * Nacos 模板 deploy/nacos-config/gsc-ai.yaml 里没有这个 key（值只存在于远程 Nacos），
 * 用模板全新部署会直接启动失败。
 */
class AiChatHistoryBudgetConfigTest {

    @Test
    @DisplayName("history-token-budget 的 @Value 必须带 :默认值，且与 Nacos 模板取值一致")
    void historyTokenBudget_hasDefaultValue() throws NoSuchFieldException {
        Field field = AiChatServiceImpl.class.getDeclaredField("historyTokenBudget");
        Value value = field.getAnnotation(Value.class);

        assertThat(value).as("historyTokenBudget 必须由 @Value 注入").isNotNull();
        assertThat(value.value())
                .as("表达式必须写成 ${key:默认值}，否则缺 Nacos 配置时启动失败")
                .contains(":3000}");
    }
}
