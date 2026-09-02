package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.InvestmentMode;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @DisplayName("조정종가 트리거 테스트")
  class AdjustedCloseTriggerTests {

    @Test
    @DisplayName("액면분할일을 폭락으로 오인해 유령매수하지 않는다(트리거는 adjustedClose 기준)")
    void executeConditional_SplitDay_NoPhantomDropBuy() {
      // given: PER_PURCHASE, 20% 하락시 매수, 최대 5회
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .userId("test-user")
        .symbol("SPLIT-TEST")
        .startDate(LocalDate.of(2024, 1, 15))
        .endDate(LocalDate.of(2024, 1, 16))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(new BigDecimal("1000.00"))
        .maxPurchases(5)
        .dropPercentage(new BigDecimal("0.2")) // 20%
        .build();

      // Day1: 분할 전 raw 100 / 조정 25 (초기매수)
      // Day2: 분할일 raw 25 / 조정 25 — raw로 보면 75%↓ 폭락처럼 보이나 실제(조정)는 평탄
      OHLCPriceDto day1 = new OHLCPriceDto("SPLIT-TEST", LocalDate.of(2024, 1, 15),
        new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("98"),
        new BigDecimal("100.00"), new BigDecimal("25.00"), 1_000_000L, "USD", true);
      OHLCPriceDto day2 = new OHLCPriceDto("SPLIT-TEST", LocalDate.of(2024, 1, 16),
        new BigDecimal("24"), new BigDecimal("26"), new BigDecimal("23"),
        new BigDecimal("25.00"), new BigDecimal("25.00"), 1_000_000L, "USD", true);
      given(marketDataClient.getOHLCPriceRange(eq("SPLIT-TEST"), eq("2024-01-15"), eq("2024-01-16")))
        .willReturn(List.of(day1, day2));
      given(marketDataClient.getBulkFxRates(any()))
        .willReturn(java.util.Map.of(
          "2024-01-15", new BigDecimal("1300.00"),
          "2024-01-16", new BigDecimal("1300.00")));
      given(marketDataClient.getLatestFxRate())
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300.00")));
      given(marketDataClient.getCurrentPrice(eq("SPLIT-TEST")))
        .willReturn(stockPrice(new BigDecimal("30.00")));
      given(marketDataClient.getDividendHistory(eq("SPLIT-TEST"), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any()))
        .willReturn(StrategyResponse.builder().strategyType(StrategyType.CONDITIONAL_PURCHASE).build());

      // when
      strategy.executeConditional(request);

      // then: 거래는 초기매수 1건뿐(분할일 유령매수 없음), 매수가는 조정종가 25
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(ConditionalStrategyRequest.class), captor.capture(), any(), any());
      List<StrategyTransaction> txs = captor.getValue();
      assertThat(txs).hasSize(1);
      assertThat(txs.get(0).getPrice()).isEqualByComparingTo("25.00");
    }

    private StockPriceDto stockPrice(BigDecimal price) {
      return new StockPriceDto("SPLIT-TEST", "Split Test", price, null, null, null, null,
        null, null, null, null, null, null, null, "USD", true, null, null);
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
