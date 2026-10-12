package com.muscat.trade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TradeProperties 테스트")
class TradePropertiesTest {

    @Test
    @DisplayName("기본값: 기본 수수료율 0.25%, 금액 소수 둘째 자리")
    void defaultValues_AreCorrect() {
        TradeProperties properties = new TradeProperties();

        assertThat(properties.getFee().getDefaultRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
        assertThat(properties.getCalculation().getPricePrecision()).isEqualTo(2);
    }
}
