package com.muscat.backtest.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.muscat.backtest.common.enums.response.BacktestResponse;
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
import java.util.List;
import java.util.Map;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.backtest.domain.dto.response.OptimalTimingResponse;
import com.muscat.backtest.domain.dto.response.TimingResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("OptimalTimingAnalyzer 테스트")
class OptimalTimingAnalyzerTest {

  @Mock
  private MarketDataClient marketDataClient;

  private OptimalTimingAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new OptimalTimingAnalyzer(marketDataClient);
  }

  @Nested
  @DisplayName("요청 검증 테스트")
  class ValidationTests {

    @Test
    @DisplayName("요청이 null이면 예외 발생")
    void validateRequest_Null_ThrowsException() {
      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(null))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
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

  @Nested
  @DisplayName("분석")
  class AnalysisTests {

    private static final String SYMBOL = "AAPL";
    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END = LocalDate.of(2024, 1, 5);

    private OHLCPriceDto day(LocalDate date, String close) {
      BigDecimal price = new BigDecimal(close);
      return new OHLCPriceDto(SYMBOL, date, price, price, price, price, price, 1_000L, "USD", true);
    }

    private OptimalTimingRequest request(LocalDate start, LocalDate end, String target) {
      // 130만원 = 환율 1300 에서 1000달러
      return OptimalTimingRequest.builder().userId("test-user").symbol(SYMBOL).startDate(start).endDate(end)
        .investmentAmount(new BigDecimal("1300000")).targetReturnPercent(new BigDecimal(target)).build();
    }

    // 현재가 100달러 · 현재 환율 1300(일별 환율은 빈 응답이라 기본 환율 1300)
    private void stubCurrent() {
      given(marketDataClient.getCurrentPrice(SYMBOL)).willReturn(new StockPriceDto(SYMBOL, null, new BigDecimal("100"),
        null, null, null, null, null, null, null, null, null, null, null, "USD", true, null, null));
      given(marketDataClient.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300")));
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());
    }

    // 1일 100 · 2일 80 · 3일 120 · 4일 시세 없음 · 5일 90. 지금 100 이면 각각 0% · +25% · -16.7% · +11.1%
    private void stubFiveDays() {
      given(marketDataClient.getOHLCPriceRange(SYMBOL, START.toString(), END.toString())).willReturn(List.of(
        day(START, "100"), day(START.plusDays(1), "80"), day(START.plusDays(2), "120"), day(END, "90")));
    }

    @Test
    @DisplayName("목표 이상인 날만 수익률 높은 순으로 순위를 매기고 1위가 최적 시점이다. 시세 없는 날은 분석일에서 빠진다")
    void ranksQualifyingDays() {
      stubCurrent();
      stubFiveDays();

      OptimalTimingResponse response = analyzer.analyzeOptimalTiming(request(START, END, "10"));

      assertThat(response.getQualifyingTimings())
        .extracting(TimingResult::getPurchaseDate, TimingResult::getRank)
        .containsExactly(tuple(START.plusDays(1), 1), tuple(END, 2));
      assertThat(response.getBestTiming().getPurchaseDate()).isEqualTo(START.plusDays(1));
      assertThat(response.getBestTiming().getTotalReturnPercent()).isEqualByComparingTo("25");
      assertThat(response.getTotalQualifyingDays()).isEqualTo(2);
      assertThat(response.getTotalAnalyzedDays()).isEqualTo(4);
      assertThat(response.getCurrentPrice()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("목표에 닿는 날이 없으면 최적 시점은 비고 분석일 수만 남는다")
    void noQualifyingDay() {
      stubCurrent();
      stubFiveDays();

      OptimalTimingResponse response = analyzer.analyzeOptimalTiming(request(START, END, "50"));

      assertThat(response.getQualifyingTimings()).isEmpty();
      assertThat(response.getBestTiming()).isNull();
      assertThat(response.getTotalQualifyingDays()).isZero();
      assertThat(response.getTotalAnalyzedDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("기간에 시세가 하나도 없으면 DATA_NOT_FOUND")
    void noPrices() {
      stubCurrent();
      given(marketDataClient.getOHLCPriceRange(SYMBOL, START.toString(), END.toString())).willReturn(List.of());

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request(START, END, "10")))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("현재가가 없으면 STOCK_DATA_NOT_FOUND")
    void noCurrentPrice() {
      given(marketDataClient.getCurrentPrice(SYMBOL)).willReturn(null);

      assertThatThrownBy(() -> analyzer.analyzeOptimalTiming(request(START, END, "10")))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STOCK_DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("종료일이 오늘 이후면 오늘까지만 조회한다")
    void futureEndDateStopsToday() {
      LocalDate today = LocalDate.now();
      stubCurrent();
      given(marketDataClient.getOHLCPriceRange(SYMBOL, today.minusDays(1).toString(), today.toString()))
        .willReturn(List.of(day(today.minusDays(1), "100")));

      OptimalTimingResponse response = analyzer.analyzeOptimalTiming(request(today.minusDays(1), today.plusDays(30), "0"));

      assertThat(response.getTotalAnalyzedDays()).isEqualTo(1);
      assertThat(response.getAnalysisDate()).isEqualTo(today);
    }
  }
}
