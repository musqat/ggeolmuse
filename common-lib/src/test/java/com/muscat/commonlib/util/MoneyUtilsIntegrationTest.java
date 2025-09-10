package com.muscat.commonlib.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * MoneyUtils 통합 테스트 - 각 모듈에서의 실제 사용 시나리오
 */
class MoneyUtilsIntegrationTest {

    @Test
    @DisplayName("Trade 모듈 시나리오: 주식 매수 거래 계산")
    void tradeModuleScenario() {
        // Given: 애플 주식 10주를 $150/주로 매수
        BigDecimal quantity = new BigDecimal("10");
        BigDecimal stockPrice = new BigDecimal("150.00");
        BigDecimal commissionRate = new BigDecimal("0.001"); // 0.1% 수수료

        // When: 거래 금액 계산
        BigDecimal tradeAmount = MoneyUtils.multiply(quantity, stockPrice);
        tradeAmount = MoneyUtils.roundUsd(tradeAmount);
        
        BigDecimal fee = MoneyUtils.multiply(tradeAmount, commissionRate);
        fee = MoneyUtils.roundUsd(fee);
        
        BigDecimal totalAmount = MoneyUtils.add(tradeAmount, fee);

        // Then: 정확한 계산 결과
        assertThat(tradeAmount).isEqualByComparingTo("1500.00");
        assertThat(fee).isEqualByComparingTo("1.50");
        assertThat(totalAmount).isEqualByComparingTo("1501.50");
    }

    @Test
    @DisplayName("Market-data 모듈 시나리오: 환율 데이터 정규화")
    void marketDataModuleScenario() {
        // Given: 외부 API에서 받은 원시 환율 데이터
        BigDecimal rawExchangeRate = new BigDecimal("1327.123456789");

        // When: MoneyUtils로 정규화
        BigDecimal normalizedRate = MoneyUtils.roundExchangeRate(rawExchangeRate);

        // Then: 소수점 6자리로 정규화됨
        assertThat(normalizedRate).isEqualByComparingTo("1327.123457");
        assertThat(normalizedRate.scale()).isEqualTo(6);
    }

    @Test
    @DisplayName("User 모듈 시나리오: KRW-USD 환전 계산")
    void userModuleScenario() {
        // Given: 100만원을 USD로 환전 (환율 1,300원)
        BigDecimal krwAmount = new BigDecimal("1000000");
        BigDecimal exchangeRate = new BigDecimal("1300.00");

        // When: 환전 계산
        MoneyUtils.validatePositiveAmount(krwAmount, "KRW 환전 금액");
        MoneyUtils.validateMinimumExchangeAmount(krwAmount, "KRW");
        
        BigDecimal usdAmount = MoneyUtils.calculateKrwToUsd(krwAmount, exchangeRate);
        
        // Then: 정확한 환전 결과
        assertThat(usdAmount).isEqualByComparingTo("769.23");
        
        // 역환전 확인
        BigDecimal backToKrw = MoneyUtils.calculateUsdToKrw(usdAmount, exchangeRate);
        assertThat(backToKrw).isEqualByComparingTo("999999");
    }

    @Test
    @DisplayName("Backtest 모듈 시나리오: 투자 수익률 계산")
    void backtestModuleScenario() {
        // Given: 1000달러 투자 → 현재 1200달러 가치
        BigDecimal initialInvestment = new BigDecimal("1000.00");
        BigDecimal currentValue = new BigDecimal("1200.00");

        // When: 수익률 계산
        BigDecimal profit = MoneyUtils.subtract(currentValue, initialInvestment);
        BigDecimal returnRate = MoneyUtils.calculateReturnRate(initialInvestment, currentValue);

        // Then: 20% 수익률
        assertThat(profit).isEqualByComparingTo("200.00");
        assertThat(returnRate).isEqualByComparingTo("20.00"); // 20%
    }

    @Test
    @DisplayName("통합 시나리오: 다국가 포트폴리오 자산 계산")
    void multiCurrencyPortfolioScenario() {
        // Given: KRW 500만원 + USD 3000달러 (환율 1,320원)
        BigDecimal krwBalance = new BigDecimal("5000000");
        BigDecimal usdBalance = new BigDecimal("3000.00");
        BigDecimal exchangeRate = new BigDecimal("1320.00");

        // When: 총 자산을 KRW 기준으로 계산
        BigDecimal usdInKrw = MoneyUtils.calculateUsdToKrw(usdBalance, exchangeRate);
        BigDecimal totalAssetsKrw = MoneyUtils.add(krwBalance, usdInKrw);
        totalAssetsKrw = MoneyUtils.roundKrw(totalAssetsKrw);

        // 포맷팅된 문자열로 표시
        String formattedAmount = MoneyUtils.formatAmount(totalAssetsKrw, "KRW");

        // Then: 정확한 총 자산 계산
        assertThat(usdInKrw).isEqualByComparingTo("3960000");
        assertThat(totalAssetsKrw).isEqualByComparingTo("8960000");
        assertThat(formattedAmount).isEqualTo("8,960,000원");
    }

    @Test
    @DisplayName("검증 기능 통합 테스트")
    void validationIntegrationTest() {
        // 양수 검증
        assertThatThrownBy(() -> 
            MoneyUtils.validatePositiveAmount(BigDecimal.ZERO, "테스트 금액")
        ).isInstanceOf(RuntimeException.class);

        // 최소 환전 금액 검증
        assertThatThrownBy(() ->
            MoneyUtils.validateMinimumExchangeAmount(new BigDecimal("500"), "KRW")
        ).isInstanceOf(RuntimeException.class);

        // 최대 환전 금액 검증
        assertThatThrownBy(() ->
            MoneyUtils.validateMaximumExchangeAmount(new BigDecimal("100000000"))
        ).isInstanceOf(RuntimeException.class);

        // 정상 케이스
        assertThatNoException().isThrownBy(() -> {
            MoneyUtils.validatePositiveAmount(new BigDecimal("1000"), "테스트");
            MoneyUtils.validateMinimumExchangeAmount(new BigDecimal("1000"), "KRW");
            MoneyUtils.validateMaximumExchangeAmount(new BigDecimal("1000000"));
        });
    }
}