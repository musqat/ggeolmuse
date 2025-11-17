package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.InvestmentMode;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConditionalPurchaseStrategy 테스트")
class ConditionalPurchaseStrategyTest {

  @Mock
  private MarketDataClient marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  private ConditionalPurchaseStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new ConditionalPurchaseStrategy(marketDataClient, responseMapper);
  }

  @Nested
  @DisplayName("전략 타입 테스트")
  class StrategyTypeTests {

    @Test
    @DisplayName("전략 타입은 CONDITIONAL_PURCHASE")
    void getStrategyType_ReturnsConditionalPurchase() {
      assertThat(strategy.getStrategyType()).isEqualTo(StrategyType.CONDITIONAL_PURCHASE);
    }
  }

  @Nested
  @DisplayName("요청 검증 테스트")
  class ValidationTests {

    @Test
    @DisplayName("요청이 null이면 예외 발생")
    void validateRequest_Null_ThrowsException() {
      assertThatThrownBy(() -> strategy.executeConditional(null))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
    }

    @Test
    @DisplayName("심볼이 null이면 예외 발생")
    void validateRequest_NullSymbol_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol(null)
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }

    @Test
    @DisplayName("심볼이 빈 문자열이면 예외 발생")
    void validateRequest_EmptySymbol_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("   ")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 null이면 예외 발생")
    void validateRequest_NullStartDate_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(null)
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }

    @Test
    @DisplayName("종료일이 null이면 예외 발생")
    void validateRequest_NullEndDate_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(null)
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 종료일보다 이후면 예외 발생")
    void validateRequest_InvalidDateRange_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 12, 31))
        .endDate(LocalDate.of(2023, 1, 1))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }

    @Test
    @DisplayName("TOTAL_BUDGET 모드에서 totalInvestment가 null이면 예외 발생")
    void validateRequest_TotalBudgetMode_NullTotalInvestment_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.TOTAL_BUDGET)
        .totalInvestment(null)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
    }

    @Test
    @DisplayName("TOTAL_BUDGET 모드에서 totalInvestment가 0 이하면 예외 발생")
    void validateRequest_TotalBudgetMode_ZeroTotalInvestment_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.TOTAL_BUDGET)
        .totalInvestment(BigDecimal.ZERO)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
    }

    @Test
    @DisplayName("PER_PURCHASE 모드에서 amountPerPurchase가 null이면 예외 발생")
    void validateRequest_PerPurchaseMode_NullAmountPerPurchase_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(null)
        .maxPurchases(5)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("회당 투자금액은 필수입니다");
    }

    @Test
    @DisplayName("PER_PURCHASE 모드에서 maxPurchases가 null이면 예외 발생")
    void validateRequest_PerPurchaseMode_NullMaxPurchases_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(BigDecimal.valueOf(1000))
        .maxPurchases(null)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("최대 매수 횟수가 필수입니다");
    }

    @Test
    @DisplayName("dropPercentage가 null이면 예외 발생")
    void validateRequest_NullDropPercentage_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(null)
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    @DisplayName("dropPercentage가 0 이하면 예외 발생")
    void validateRequest_ZeroDropPercentage_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.ZERO)
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    @DisplayName("maxPurchases가 0 이하면 예외 발생")
    void validateRequest_ZeroMaxPurchases_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .maxPurchases(0)
        .build();

      assertThatThrownBy(() -> strategy.executeConditional(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.INVALID_MAX_PURCHASES);
    }
  }
}
