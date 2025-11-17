package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.OptimalTimingRequest;
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
@DisplayName("OptimalTimingStrategy 테스트")
class OptimalTimingStrategyTest {

  @Mock
  private MarketDataClient marketDataClient;

  private OptimalTimingStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new OptimalTimingStrategy(marketDataClient);
  }

  @Nested
  @DisplayName("요청 검증 테스트")
  class ValidationTests {

    @Test
    @DisplayName("요청이 null이면 예외 발생")
    void validateRequest_Null_ThrowsException() {
      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(null))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
    }

    @Test
    @DisplayName("심볼이 null이면 예외 발생")
    void validateRequest_NullSymbol_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol(null)
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }

    @Test
    @DisplayName("심볼이 빈 문자열이면 예외 발생")
    void validateRequest_EmptySymbol_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("   ")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 null이면 예외 발생")
    void validateRequest_NullStartDate_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(null)
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }

    @Test
    @DisplayName("종료일이 null이면 예외 발생")
    void validateRequest_NullEndDate_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(null)
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 종료일보다 이후면 예외 발생")
    void validateRequest_InvalidDateRange_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 12, 31))
        .endDate(LocalDate.of(2023, 1, 1))
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }

    @Test
    @DisplayName("투자 금액이 null이면 예외 발생")
    void validateRequest_NullInvestmentAmount_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(null)
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.INVALID_REQUEST);
    }

    @Test
    @DisplayName("투자 금액이 0이면 예외 발생")
    void validateRequest_ZeroInvestmentAmount_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.ZERO)
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.INVALID_REQUEST);
    }

    @Test
    @DisplayName("투자 금액이 음수면 예외 발생")
    void validateRequest_NegativeInvestmentAmount_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.valueOf(-1000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.INVALID_REQUEST);
    }

    @Test
    @DisplayName("목표 수익률이 null이면 예외 발생")
    void validateRequest_NullTargetReturnPercent_ThrowsException() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(null)
        .build();

      assertThatThrownBy(() -> strategy.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.INVALID_REQUEST);
    }
  }

  @Nested
  @DisplayName("정상 요청 생성 테스트")
  class ValidRequestTests {

    @Test
    @DisplayName("모든 필수 필드가 올바르게 설정된 요청 생성")
    void createValidRequest_Success() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .userId("test-user")
        .build();

      assertThat(request.getSymbol()).isEqualTo("AAPL");
      assertThat(request.getStartDate()).isEqualTo(LocalDate.of(2023, 1, 1));
      assertThat(request.getEndDate()).isEqualTo(LocalDate.of(2023, 12, 31));
      assertThat(request.getInvestmentAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
      assertThat(request.getTargetReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    @DisplayName("양수 목표 수익률 설정 가능")
    void createValidRequest_PositiveTargetReturn() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("GOOGL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 6, 30))
        .investmentAmount(BigDecimal.valueOf(50000))
        .targetReturnPercent(BigDecimal.valueOf(15.5))
        .build();

      assertThat(request.getTargetReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(15.5));
    }

    @Test
    @DisplayName("음수 목표 수익률도 설정 가능 (손실 회피 시나리오)")
    void createValidRequest_NegativeTargetReturn() {
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("TSLA")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 3, 31))
        .investmentAmount(BigDecimal.valueOf(20000))
        .targetReturnPercent(BigDecimal.valueOf(-5))
        .build();

      assertThat(request.getTargetReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(-5));
    }

    @Test
    @DisplayName("같은 날짜 범위 설정 가능 (단일일 분석)")
    void createValidRequest_SameStartEndDate() {
      LocalDate sameDate = LocalDate.of(2023, 6, 15);
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("MSFT")
        .startDate(sameDate)
        .endDate(sameDate)
        .investmentAmount(BigDecimal.valueOf(10000))
        .targetReturnPercent(BigDecimal.valueOf(5))
        .build();

      assertThat(request.getStartDate()).isEqualTo(request.getEndDate());
    }
  }
}
