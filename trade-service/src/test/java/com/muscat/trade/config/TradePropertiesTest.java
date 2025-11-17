package com.muscat.trade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TradeProperties 테스트")
class TradePropertiesTest {

    @Test
    @DisplayName("기본 설정값 확인")
    void defaultValues_AreCorrect() {
        // When
        TradeProperties properties = new TradeProperties();

        // Then - Fee 기본값
        assertThat(properties.getFee()).isNotNull();
        assertThat(properties.getFee().getDefaultRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
        assertThat(properties.getFee().getMinimumAmount()).isEqualByComparingTo(new BigDecimal("1.00"));

        // Then - Calculation 기본값
        assertThat(properties.getCalculation()).isNotNull();
        assertThat(properties.getCalculation().getPricePrecision()).isEqualTo(2);
        assertThat(properties.getCalculation().getQuantityPrecision()).isEqualTo(6);

        // Then - Validation 기본값
        assertThat(properties.getValidation()).isNotNull();
        assertThat(properties.getValidation().getMaxPriceDeviation()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(properties.getValidation().getMinTradeAmount()).isEqualByComparingTo(new BigDecimal("1.00"));

        // Then - Cache 기본값
        assertThat(properties.getCache()).isNotNull();
        assertThat(properties.getCache().getMarketDataTtl()).isEqualTo(300);
    }

    @Test
    @DisplayName("Fee 설정값 변경")
    void setFeeProperties_Success() {
        // Given
        TradeProperties properties = new TradeProperties();
        TradeProperties.Fee fee = new TradeProperties.Fee();
        fee.setDefaultRate(new BigDecimal("0.005"));
        fee.setMinimumAmount(new BigDecimal("2.00"));

        // When
        properties.setFee(fee);

        // Then
        assertThat(properties.getFee().getDefaultRate()).isEqualByComparingTo(new BigDecimal("0.005"));
        assertThat(properties.getFee().getMinimumAmount()).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    @DisplayName("Calculation 설정값 변경")
    void setCalculationProperties_Success() {
        // Given
        TradeProperties properties = new TradeProperties();
        TradeProperties.Calculation calculation = new TradeProperties.Calculation();
        calculation.setPricePrecision(4);
        calculation.setQuantityPrecision(8);

        // When
        properties.setCalculation(calculation);

        // Then
        assertThat(properties.getCalculation().getPricePrecision()).isEqualTo(4);
        assertThat(properties.getCalculation().getQuantityPrecision()).isEqualTo(8);
    }

    @Test
    @DisplayName("Validation 설정값 변경")
    void setValidationProperties_Success() {
        // Given
        TradeProperties properties = new TradeProperties();
        TradeProperties.Validation validation = new TradeProperties.Validation();
        validation.setMaxPriceDeviation(new BigDecimal("0.30"));
        validation.setMinTradeAmount(new BigDecimal("5.00"));

        // When
        properties.setValidation(validation);

        // Then
        assertThat(properties.getValidation().getMaxPriceDeviation()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(properties.getValidation().getMinTradeAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("Cache 설정값 변경")
    void setCacheProperties_Success() {
        // Given
        TradeProperties properties = new TradeProperties();
        TradeProperties.Cache cache = new TradeProperties.Cache();
        cache.setMarketDataTtl(600);

        // When
        properties.setCache(cache);

        // Then
        assertThat(properties.getCache().getMarketDataTtl()).isEqualTo(600);
    }
}
