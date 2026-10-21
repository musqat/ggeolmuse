package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.common.enums.response.BacktestResponse;

@DisplayName("BacktestCalculationUtils 단위 테스트")
class BacktestCalculationUtilsTest {

  @Nested
  @DisplayName("주식 수량 계산 테스트")
  class CalculateSharesTests {

    @Test
    @DisplayName("정상적인 주식 수량 계산")
    void calculateShares_Success() {
      // given
      BigDecimal investmentAmount = new BigDecimal("1300000"); // 1,300,000 KRW
      BigDecimal fxRate = new BigDecimal("1300"); // 1 USD = 1,300 KRW
      BigDecimal stockPrice = new BigDecimal("100"); // $100 per share

      // when
      BigDecimal shares = BacktestCalculationUtils.calculateShares(investmentAmount, fxRate,
        stockPrice);

      // then - 1,300,000 / 1,300 / 100 = 10 shares
      assertThat(shares).isEqualByComparingTo(new BigDecimal("10.00000000"));
    }

    @Test
    @DisplayName("투자금액이 null인 경우 예외 발생")
    void calculateShares_NullInvestment_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateShares(null, new BigDecimal("1300"),
          new BigDecimal("100")))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("null");
    }

    @Test
    @DisplayName("환율이 0인 경우 예외 발생")
    void calculateShares_ZeroFxRate_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateShares(new BigDecimal("1300000"), BigDecimal.ZERO,
          new BigDecimal("100")))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("환율");
    }

    @Test
    @DisplayName("주식가격이 0인 경우 예외 발생")
    void calculateShares_ZeroStockPrice_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateShares(new BigDecimal("1300000"), new BigDecimal("1300"),
          BigDecimal.ZERO))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("주식가격");
    }
  }

  @Nested
  @DisplayName("평균 가격 계산 테스트")
  class CalculateAveragePriceTests {

    @Test
    @DisplayName("정상적인 평균 가격 계산")
    void calculateAveragePrice_Success() {
      // given
      // 130만원씩 두 번, 환율 1300. totalFxRateSum = Σ(투자금액 × 환율)
      BigDecimal totalInvestment = new BigDecimal("2600000");
      BigDecimal totalFxRateSum = new BigDecimal("3380000000");
      BigDecimal totalShares = new BigDecimal("20");

      // when
      BigDecimal avgPrice = BacktestCalculationUtils.calculateAveragePrice(totalInvestment,
        totalFxRateSum, totalShares);

      // then - 2,600,000 / 1,300 / 20 = 100
      assertThat(avgPrice).isEqualByComparingTo(new BigDecimal("100.00000000"));
    }

    @Test
    @DisplayName("환율 합계가 0인 경우 예외 발생")
    void calculateAveragePrice_ZeroFxRateSum_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateAveragePrice(
          new BigDecimal("2600000"), BigDecimal.ZERO, new BigDecimal("20")))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("평균 환율 계산 테스트")
  class CalculateAverageFxRateTests {

    @Test
    @DisplayName("정상적인 평균 환율 계산 (가중평균)")
    void calculateAverageFxRate_Success() {
      // given
      // (1000 × 1300 + 2000 × 1400 + 1000 × 1200) / 4000 = 1325
      BigDecimal totalFxRateSum = new BigDecimal("5300000");
      BigDecimal totalInvested = new BigDecimal("4000");

      // when
      BigDecimal avgRate = BacktestCalculationUtils.calculateAverageFxRate(totalFxRateSum,
        totalInvested);

      // then - 5,300,000 / 4000 = 1325.00
      assertThat(avgRate).isEqualByComparingTo(new BigDecimal("1325.00"));
    }

    @Test
    @DisplayName("총 투자금액이 0 이하인 경우 예외 발생")
    void calculateAverageFxRate_InvalidInvested_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateAverageFxRate(new BigDecimal("5300000"), BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("중간값 계산 테스트")
  class CalculateMedianTests {

    @Test
    @DisplayName("홀수 개의 값 - 중간값 계산")
    void calculateMedian_OddValues() {
      // given
      List<BigDecimal> values = Arrays.asList(
        new BigDecimal("1"),
        new BigDecimal("3"),
        new BigDecimal("5"),
        new BigDecimal("7"),
        new BigDecimal("9")
      );

      // when
      BigDecimal median = BacktestCalculationUtils.calculateMedian(values);

      // then - 중간값은 5
      assertThat(median).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("짝수 개의 값 - 중간 두 값의 평균")
    void calculateMedian_EvenValues() {
      // given
      List<BigDecimal> values = Arrays.asList(
        new BigDecimal("1"),
        new BigDecimal("3"),
        new BigDecimal("5"),
        new BigDecimal("7")
      );

      // when
      BigDecimal median = BacktestCalculationUtils.calculateMedian(values);

      // then - 중간값은 (3 + 5) / 2 = 4
      assertThat(median).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    @DisplayName("빈 리스트인 경우 예외 발생")
    void calculateMedian_EmptyList_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateMedian(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("값 스케일 조정 테스트")
  class ScaleValueTests {

    @Test
    @DisplayName("지정된 스케일로 반올림")
    void scaleValue_Success() {
      // given
      BigDecimal value = new BigDecimal("123.456789");

      // when
      BigDecimal scaled = BacktestCalculationUtils.scaleValue(value, 2);

      // then
      assertThat(scaled).isEqualByComparingTo(new BigDecimal("123.46"));
    }

    @Test
    @DisplayName("null 값인 경우 예외 발생")
    void scaleValue_Null_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.scaleValue(null, 2))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("주식 수량 스케일 테스트")
  class ScaleSharesTests {

    @Test
    @DisplayName("주식 수량을 표준 스케일(6)로 반올림")
    void scaleShares_Success() {
      // given
      BigDecimal shares = new BigDecimal("10.123456789");

      // when
      BigDecimal scaled = BacktestCalculationUtils.scaleShares(shares);

      // then - 6자리까지만
      assertThat(scaled).isEqualByComparingTo(new BigDecimal("10.123457"));
    }
  }

  @Nested
  @DisplayName("평균 수익률 계산 테스트")
  class CalculateAverageReturnTests {

    @Test
    @DisplayName("정상적인 평균 수익률 계산")
    void calculateAverageReturn_Success() {
      // given
      List<BigDecimal> returns = Arrays.asList(
        new BigDecimal("10"),
        new BigDecimal("20"),
        new BigDecimal("30")
      );

      // when
      BigDecimal avgReturn = BacktestCalculationUtils.calculateAverageReturn(returns);

      // then - (10 + 20 + 30) / 3 = 20
      assertThat(avgReturn).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("빈 리스트인 경우 예외 발생")
    void calculateAverageReturn_EmptyList_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateAverageReturn(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("분할 투자 금액 계산 테스트")
  class CalculateInvestmentPerDivisionTests {

    @Test
    @DisplayName("총 투자금액을 분할 횟수로 나누기")
    void calculateInvestmentPerDivision_Success() {
      // given
      BigDecimal totalInvestment = new BigDecimal("1200000");
      int divisions = 12;

      // when
      BigDecimal perDivision = BacktestCalculationUtils.calculateInvestmentPerDivision(
        totalInvestment, divisions);

      // then - 1,200,000 / 12 = 100,000
      assertThat(perDivision).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("분할 횟수가 0 이하인 경우 예외 발생")
    void calculateInvestmentPerDivision_InvalidDivisions_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateInvestmentPerDivision(new BigDecimal("1200000"), 0))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("배당금 계산 테스트 (고정 주식수)")
  class CalculateTotalDividendsFixedSharesTests {

    @Test
    @DisplayName("기간 내 배당금 합계 계산")
    void calculateTotalDividends_Success() {
      // given
      DividendHistoryDto.DividendPayment div1 = new DividendHistoryDto.DividendPayment();
      div1.setExDate(LocalDate.of(2024, 3, 15));
      div1.setAmount(new BigDecimal("0.50"));

      DividendHistoryDto.DividendPayment div2 = new DividendHistoryDto.DividendPayment();
      div2.setExDate(LocalDate.of(2024, 6, 15));
      div2.setAmount(new BigDecimal("0.50"));

      DividendHistoryDto.DividendPayment div3 = new DividendHistoryDto.DividendPayment();
      div3.setExDate(LocalDate.of(2024, 9, 15));
      div3.setAmount(new BigDecimal("0.50"));

      DividendHistoryDto history = new DividendHistoryDto();
      history.setDividends(Arrays.asList(div1, div2, div3));

      BigDecimal shares = new BigDecimal("100");
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      // when
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        history, shares, startDate, endDate);

      // then - 0.50 * 100 * 3 quarters = $150
      assertThat(totalDividends).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("배당 데이터가 없는 경우 0 반환")
    void calculateTotalDividends_NoDividends_ReturnsZero() {
      // given
      DividendHistoryDto history = new DividendHistoryDto();
      history.setDividends(Collections.emptyList());

      // when
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        history, new BigDecimal("100"),
        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

      // then
      assertThat(totalDividends).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("null 파라미터인 경우 0 반환")
    void calculateTotalDividends_NullParams_ReturnsZero() {
      // when
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        null, new BigDecimal("100"),
        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

      // then
      assertThat(totalDividends).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("배당락일 경계 (실제 거래일 < 배당락일만 받는다)")
  class DividendExDateBoundaryTests {

    private final LocalDate exDate = LocalDate.of(2024, 3, 18); // 월요일

    private DividendHistoryDto history() {
      DividendHistoryDto.DividendPayment pay = new DividendHistoryDto.DividendPayment();
      pay.setExDate(exDate);
      pay.setAmount(new BigDecimal("0.50"));
      DividendHistoryDto h = new DividendHistoryDto();
      h.setDividends(List.of(pay));
      return h;
    }

    private com.muscat.backtest.domain.model.StrategyTransaction buy(LocalDate planned, LocalDate actual) {
      return com.muscat.backtest.domain.model.StrategyTransaction.builder()
        .date(planned).actualDate(actual).shares(new BigDecimal("10")).build();
    }

    @Test
    @DisplayName("배당락일 전날 매수만 받고, 당일 · 이후 매수와 빈 날짜는 못 받는다")
    void receivesDividend() {
      assertThat(BacktestCalculationUtils.receivesDividend(exDate.minusDays(1), exDate)).isTrue();
      assertThat(BacktestCalculationUtils.receivesDividend(exDate, exDate)).isFalse();
      assertThat(BacktestCalculationUtils.receivesDividend(exDate.plusDays(1), exDate)).isFalse();
      assertThat(BacktestCalculationUtils.receivesDividend(null, exDate)).isFalse();
    }

    @Test
    @DisplayName("단순 매수: 매수일이 배당락일이면 그 배당은 빠진다")
    void fixedShares_PurchaseOnExDate_Excluded() {
      assertThat(BacktestCalculationUtils.calculateTotalDividends(history(), new BigDecimal("100"), exDate, exDate.plusMonths(3)))
        .isEqualByComparingTo("0");
      assertThat(BacktestCalculationUtils.calculateTotalDividends(history(), new BigDecimal("100"), exDate.minusDays(1), exDate.plusMonths(3)))
        .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("적립식: 토요일에 계획해 배당락일(월요일)에 실제로 산 주식은 못 받고, 전 거래일에 산 주식만 받는다")
    void transactions_UseActualTradeDate() {
      var plannedSaturdayBoughtMonday = buy(exDate.minusDays(2), exDate);
      var boughtFriday = buy(exDate.minusDays(3), exDate.minusDays(3));

      assertThat(BacktestCalculationUtils.calculateTotalDividends(history(), List.of(plannedSaturdayBoughtMonday),
        exDate.minusDays(2), exDate.plusMonths(3))).isEqualByComparingTo("0");
      assertThat(BacktestCalculationUtils.calculateTotalDividends(history(), List.of(plannedSaturdayBoughtMonday, boughtFriday),
        exDate.minusDays(3), exDate.plusMonths(3))).isEqualByComparingTo("5.00");
    }
  }

  @Nested
  @DisplayName("배당 수익률 계산 테스트")
  class CalculateDividendYieldTests {

    @Test
    @DisplayName("정상적인 배당 수익률 계산")
    void calculateDividendYield_Success() {
      // given
      BigDecimal totalDividends = new BigDecimal("200"); // $200 in dividends
      BigDecimal shares = new BigDecimal("100");
      BigDecimal currentPrice = new BigDecimal("50"); // $50 per share

      // when
      BigDecimal yield = BacktestCalculationUtils.calculateDividendYield(totalDividends, shares,
        currentPrice);

      // then - 200 / (100 * 50) * 100 = 4%
      assertThat(yield).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    @DisplayName("주식수가 0인 경우 0 반환")
    void calculateDividendYield_ZeroShares_ReturnsZero() {
      // when
      BigDecimal yield = BacktestCalculationUtils.calculateDividendYield(
        new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("50"));

      // then
      assertThat(yield).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("수수료 포함 주식 수량 계산 테스트")
  class CalculateSharesWithFeeTests {

    @Test
    @DisplayName("수수료 공제 후 주식 수량 계산")
    void calculateSharesWithFee_Success() {
      // given
      BigDecimal usdAmount = new BigDecimal("1000");
      BigDecimal stockPrice = new BigDecimal("100");
      BigDecimal feeRate = new BigDecimal("0.01"); // 1% fee

      // when
      BigDecimal shares = BacktestCalculationUtils.calculateSharesWithFee(usdAmount, stockPrice,
        feeRate);

      // then - (1000 - 10) / 100 = 9.9 shares
      assertThat(shares).isEqualByComparingTo(new BigDecimal("9.90000000"));
    }

    @Test
    @DisplayName("주식가격이 0인 경우 예외 발생")
    void calculateSharesWithFee_ZeroPrice_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateSharesWithFee(
          new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("0.01")))
        .isInstanceOf(BacktestException.class);
    }
  }

  @Nested
  @DisplayName("매매 수수료 계산 테스트")
  class CalculateTradingFeeTests {

    @Test
    @DisplayName("정상적인 수수료 계산")
    void calculateTradingFee_Success() {
      // given
      BigDecimal usdAmount = new BigDecimal("1000");
      BigDecimal feeRate = new BigDecimal("0.01"); // 1%

      // when
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(usdAmount, feeRate);

      // then - 1000 * 0.01 = 10
      assertThat(fee).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("null 파라미터인 경우 0 반환")
    void calculateTradingFee_NullParams_ReturnsZero() {
      // when
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(null, new BigDecimal("0.01"));

      // then
      assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("총 비용 계산 테스트")
  class CalculateTotalCostTests {

    @Test
    @DisplayName("주식 구매비 + 수수료")
    void calculateTotalCost_Success() {
      // given
      BigDecimal shares = new BigDecimal("10");
      BigDecimal stockPrice = new BigDecimal("100");
      BigDecimal tradingFee = new BigDecimal("10");

      // when
      BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(shares, stockPrice,
        tradingFee);

      // then - (10 * 100) + 10 = 1010
      assertThat(totalCost).isEqualByComparingTo(new BigDecimal("1010.00"));
    }

    @Test
    @DisplayName("수수료가 null인 경우 0으로 처리")
    void calculateTotalCost_NullFee_TreatsAsZero() {
      // given
      BigDecimal shares = new BigDecimal("10");
      BigDecimal stockPrice = new BigDecimal("100");

      // when
      BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(shares, stockPrice, null);

      // then - 10 * 100 = 1000
      assertThat(totalCost).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("null 파라미터인 경우 예외 발생")
    void calculateTotalCost_NullParams_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateTotalCost(null, new BigDecimal("100"),
          new BigDecimal("10")))
        .isInstanceOf(BacktestException.class);
    }
  }

  @Nested
  @DisplayName("잔액 계산 테스트")
  class CalculateRemainingCashTests {

    @Test
    @DisplayName("초기 금액 - 총 비용")
    void calculateRemainingCash_Success() {
      // given
      BigDecimal initialAmount = new BigDecimal("10000");
      BigDecimal totalCost = new BigDecimal("1010");

      // when
      BigDecimal remaining = BacktestCalculationUtils.calculateRemainingCash(initialAmount,
        totalCost);

      // then - 10000 - 1010 = 8990
      assertThat(remaining).isEqualByComparingTo(new BigDecimal("8990.00"));
    }

    @Test
    @DisplayName("null 파라미터인 경우 예외 발생")
    void calculateRemainingCash_NullParams_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        BacktestCalculationUtils.calculateRemainingCash(null, new BigDecimal("1010")))
        .isInstanceOf(BacktestException.class);
    }
  }

  @Nested
  @DisplayName("매수 수수료")
  class PurchaseFeeTests {

    @Test
    @DisplayName("투자금을 환율로 바꾼 달러에 수수료율을 곱한다")
    void calculatePurchaseFee() {
      assertThat(BacktestCalculationUtils.calculatePurchaseFee(
        new BigDecimal("1300000"), new BigDecimal("1300"), new BigDecimal("0.01")))
        .isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("수수료율이 없거나 0 이면 0")
    void calculatePurchaseFee_NoRate() {
      assertThat(BacktestCalculationUtils.calculatePurchaseFee(
        new BigDecimal("1300000"), new BigDecimal("1300"), null)).isEqualByComparingTo("0");
      assertThat(BacktestCalculationUtils.calculatePurchaseFee(
        new BigDecimal("1300000"), new BigDecimal("1300"), BigDecimal.ZERO))
        .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("수수료를 뺀 달러로 주식 수를 센다")
    void calculateSharesAfterFee() {
      assertThat(BacktestCalculationUtils.calculateSharesAfterFee(
        new BigDecimal("1300000"), new BigDecimal("1300"), new BigDecimal("100"),
        new BigDecimal("10.00")))
        .isEqualByComparingTo("9.9");
    }

    @Test
    @DisplayName("수수료가 0 이면 calculateShares 와 같다")
    void calculateSharesAfterFee_NoFee() {
      BigDecimal amount = new BigDecimal("1000000");
      BigDecimal fxRate = new BigDecimal("1337.77");
      BigDecimal price = new BigDecimal("187.31");

      assertThat(BacktestCalculationUtils.calculateSharesAfterFee(amount, fxRate, price,
        BigDecimal.ZERO))
        .isEqualTo(BacktestCalculationUtils.calculateShares(amount, fxRate, price));
    }
  }

  @Nested
  @DisplayName("입력 경계")
  class InputBoundaryTests {

    @Test
    @DisplayName("평균 단가 · 평균 환율 · 분할 금액은 null 을 받지 않는다")
    void rejectsNull() {
      assertThatThrownBy(() -> BacktestCalculationUtils.calculateAveragePrice(null, BigDecimal.ONE, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> BacktestCalculationUtils.calculateAverageFxRate(null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> BacktestCalculationUtils.calculateInvestmentPerDivision(null, 10))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("배당 합계는 입력이 비면 0 이다")
    void totalDividendsNullIsZero() {
      List<StrategyTransaction> none = List.of();

      assertThat(BacktestCalculationUtils.calculateTotalDividends(null, none, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
        .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("수수료 반영 주식 수: null 은 계산 오류, 수수료가 투자금 이상이거나 너무 적어 한 조각도 못 사면 투자금 부족")
    void sharesWithFeeBoundaries() {
      assertThatThrownBy(() -> BacktestCalculationUtils.calculateSharesWithFee(null, BigDecimal.TEN, BigDecimal.ZERO))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.SHARES_CALCULATION_ERROR);
      assertThatThrownBy(() -> BacktestCalculationUtils.calculateSharesWithFee(new BigDecimal("100"), BigDecimal.TEN, BigDecimal.ONE))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INSUFFICIENT_INVESTMENT);
      assertThatThrownBy(() -> BacktestCalculationUtils.calculateSharesWithFee(new BigDecimal("0.000001"), new BigDecimal("1000"), BigDecimal.ZERO))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INSUFFICIENT_INVESTMENT);
    }
  }

  @Nested
  @DisplayName("배당 합계 · 배당 수익률의 걸러지는 경우")
  class DividendFilterTests {

    private final LocalDate start = LocalDate.of(2024, 1, 1);
    private final LocalDate end = LocalDate.of(2024, 12, 31);

    private DividendHistoryDto.DividendPayment pay(LocalDate exDate, String amount) {
      DividendHistoryDto.DividendPayment pay = new DividendHistoryDto.DividendPayment();
      pay.setExDate(exDate);
      pay.setAmount(amount == null ? null : new BigDecimal(amount));
      return pay;
    }

    // 권리락일 없음 · 기간 전 · 기간 뒤 · 금액 없음은 0 이고, 기간 안의 주당 1달러만 센다
    private DividendHistoryDto mixed() {
      DividendHistoryDto history = new DividendHistoryDto();
      history.setDividends(List.of(pay(null, "5"), pay(LocalDate.of(2023, 12, 1), "5"), pay(LocalDate.of(2025, 1, 2), "5"),
        pay(LocalDate.of(2024, 3, 1), null), pay(LocalDate.of(2024, 6, 1), "1")));
      return history;
    }

    @Test
    @DisplayName("거래 목록 기준: 기간 밖 · 권리락일 없음 · 금액 없음은 빼고 그날 보유 주식으로 센다")
    void byTransactions() {
      List<StrategyTransaction> txs = List.of(StrategyTransaction.builder().date(start).actualDate(start)
        .shares(BigDecimal.TEN).amount(BigDecimal.ONE).fxRate(BigDecimal.ONE).build());

      assertThat(BacktestCalculationUtils.calculateTotalDividends(mixed(), txs, start, end)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("고정 주식 수 기준도 같은 배당만 센다")
    void byFixedShares() {
      assertThat(BacktestCalculationUtils.calculateTotalDividends(mixed(), BigDecimal.TEN, start, end)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("배당 목록이 없거나 비면 두 방식 모두 0")
    void emptyHistory() {
      DividendHistoryDto nullList = new DividendHistoryDto();
      DividendHistoryDto empty = new DividendHistoryDto();
      empty.setDividends(List.of());
      List<StrategyTransaction> none = List.of();

      for (DividendHistoryDto history : List.of(nullList, empty)) {
        assertThat(BacktestCalculationUtils.calculateTotalDividends(history, none, start, end)).isEqualByComparingTo("0");
        assertThat(BacktestCalculationUtils.calculateTotalDividends(history, BigDecimal.TEN, start, end)).isEqualByComparingTo("0");
      }
      assertThat(BacktestCalculationUtils.calculateTotalDividends(mixed(), (BigDecimal) null, start, end)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("배당 수익률은 주식 수나 현재가가 0 이거나 없으면 0")
    void dividendYieldGuards() {
      assertThat(BacktestCalculationUtils.calculateDividendYield(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN)).isEqualByComparingTo("0");
      assertThat(BacktestCalculationUtils.calculateDividendYield(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO)).isEqualByComparingTo("0");
      assertThat(BacktestCalculationUtils.calculateDividendYield(BigDecimal.TEN, BigDecimal.TEN, null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("거래일이나 권리락일이 없으면 배당을 받지 않는다")
    void receivesDividendNulls() {
      assertThat(BacktestCalculationUtils.receivesDividend(null, end)).isFalse();
      assertThat(BacktestCalculationUtils.receivesDividend(start, null)).isFalse();
    }
  }
}
