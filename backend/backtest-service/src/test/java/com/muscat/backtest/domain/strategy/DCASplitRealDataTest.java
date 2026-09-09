package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 분할이 낀 구간을 야후 실데이터로 검증한다. AAPL 은 2020-08-31 에 4:1 분할했다.
 * 같은 데이터를 adjustedClose 로 계산하면 96.77% 로 같다. 분할이 계산을 깨뜨리지 않는다는 뜻이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DCA 실데이터 회귀 - 분할 구간 (AAPL 2018-09 ~ 2022-09)")
class DCASplitRealDataTest {

  private static final String SYMBOL = "AAPL";
  private static final BigDecimal FX = new BigDecimal("1300");

  @Mock
  private MarketDataClient marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  @InjectMocks
  private DCAStrategy dcaStrategy;

  private static final List<OHLCPriceDto> BARS = List.of(
      bar("2018-08-31", "56.9075", "53.7749"),
      bar("2018-10-01", "56.8150", "53.6875"),
      bar("2018-11-01", "55.5550", "52.4969"),
      bar("2018-11-08", "52.1225", "49.4252"),
      bar("2018-11-30", "44.6450", "42.3346"),
      bar("2018-12-31", "39.4350", "37.3942"),
      bar("2019-02-01", "41.6300", "39.4756"),
      bar("2019-02-08", "42.6025", "40.5711"),
      bar("2019-03-01", "43.7425", "41.6567"),
      bar("2019-04-01", "47.8100", "45.5303"),
      bar("2019-05-01", "52.6300", "50.1204"),
      bar("2019-05-10", "49.2950", "47.1252"),
      bar("2019-05-31", "43.7675", "41.8411"),
      bar("2019-07-01", "50.3875", "48.1696"),
      bar("2019-08-01", "52.1075", "49.8140"),
      bar("2019-08-09", "50.2475", "48.2183"),
      bar("2019-08-30", "52.1850", "50.0776"),
      bar("2019-10-01", "56.1475", "53.8801"),
      bar("2019-11-01", "63.9550", "61.3723"),
      bar("2019-11-07", "64.8575", "62.4252"),
      bar("2019-11-29", "66.8125", "64.3069"),
      bar("2019-12-31", "73.4125", "70.6594"),
      bar("2020-01-31", "77.3775", "74.4757"),
      bar("2020-02-07", "80.0075", "77.1898"),
      bar("2020-02-28", "68.3400", "65.9332"),
      bar("2020-04-01", "60.2275", "58.1064"),
      bar("2020-05-01", "72.2675", "69.7224"),
      bar("2020-05-08", "77.5325", "75.0044"),
      bar("2020-06-01", "80.4625", "77.8389"),
      bar("2020-07-01", "91.0275", "88.0594"),
      bar("2020-07-31", "106.2600", "102.7953"),
      bar("2020-08-07", "111.1125", "107.6833"),
      bar("2020-09-01", "134.1800", "130.0389"),
      bar("2020-10-01", "116.7900", "113.1856"),
      bar("2020-10-30", "108.8600", "105.5003"),
      bar("2020-11-06", "118.6900", "115.2254"),
      bar("2020-12-01", "122.7200", "119.1378"),
      bar("2020-12-31", "132.6900", "128.8167"),
      bar("2021-02-01", "134.1400", "130.2244"),
      bar("2021-02-05", "136.7600", "132.9663"),
      bar("2021-03-01", "127.7900", "124.2452"),
      bar("2021-04-01", "123.0000", "119.5880"),
      bar("2021-04-30", "131.4600", "127.8133"),
      bar("2021-05-07", "130.2100", "126.8131"),
      bar("2021-06-01", "124.2800", "121.0378"),
      bar("2021-07-01", "137.2700", "133.6889"),
      bar("2021-07-30", "145.8600", "142.0548"),
      bar("2021-08-06", "146.1400", "142.5407"),
      bar("2021-09-01", "152.5100", "148.7539"),
      bar("2021-10-01", "142.6500", "139.1367"),
      bar("2021-11-01", "148.9600", "145.2913"),
      bar("2021-11-05", "151.2800", "147.7695"),
      bar("2021-12-01", "164.7700", "160.9465"),
      bar("2021-12-31", "177.5700", "173.4494"),
      bar("2022-02-01", "174.6100", "170.5581"),
      bar("2022-02-04", "172.3900", "168.6042"),
      bar("2022-03-01", "163.2000", "159.6160"),
      bar("2022-04-01", "174.3100", "170.4820"),
      bar("2022-04-29", "157.6500", "154.1879"),
      bar("2022-05-06", "157.2800", "154.0520"),
      bar("2022-06-01", "148.7100", "145.6579"),
      bar("2022-07-01", "138.9300", "136.0786"),
      bar("2022-08-01", "161.5100", "158.1952"),
      bar("2022-08-05", "165.3500", "162.1814"),
      bar("2022-09-01", "157.9600", "154.9330")
  );

  private static final List<DividendDto> DIVIDENDS = List.of(
      div("2018-11-08", "0.1825"),
      div("2019-02-08", "0.1825"),
      div("2019-05-10", "0.1925"),
      div("2019-08-09", "0.1925"),
      div("2019-11-07", "0.1925"),
      div("2020-02-07", "0.1925"),
      div("2020-05-08", "0.205"),
      div("2020-08-07", "0.205"),
      div("2020-11-06", "0.205"),
      div("2021-02-05", "0.205"),
      div("2021-05-07", "0.22"),
      div("2021-08-06", "0.22"),
      div("2021-11-05", "0.22"),
      div("2022-02-04", "0.22"),
      div("2022-05-06", "0.23"),
      div("2022-08-05", "0.23")
  );

  private static OHLCPriceDto bar(String date, String close, String adj) {
    BigDecimal c = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, LocalDate.parse(date), c, c, c, c,
        new BigDecimal(adj), 1_000_000L, "USD", true);
  }

  private static DividendDto div(String date, String amount) {
    return new DividendDto(SYMBOL, LocalDate.parse(date), null, null,
        new BigDecimal(amount), "USD", "yahoo");
  }

  @Test
  @DisplayName("4:1 분할을 사이에 두고도 매수가가 이어지고 수익률이 맞는다")
  void executeDca_acrossSplit() {
    DcaStrategyRequest request = DcaStrategyRequest.builder()
      .userId("test-user")
      .symbol(SYMBOL)
      .startDate(LocalDate.of(2018, 9, 1))
      .endDate(LocalDate.of(2022, 9, 1))
      .monthlyAmount(new BigDecimal("100000"))
      .purchaseDay(1)
      .investmentInterval(1)
      .purchaseFxRate(FX)
      .currentFxRate(FX)
      .reinvestDividends(true)
      .dividendTaxRate(BigDecimal.ZERO)
      .build();

    Map<LocalDate, OHLCPriceDto> byDate = BARS.stream()
        .collect(Collectors.toMap(OHLCPriceDto::date, Function.identity()));

    given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString()))
      .willReturn(BARS);
    given(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString()))
      .willReturn(DIVIDENDS);
    given(marketDataClient.getOHLCPrice(eq(SYMBOL), anyString()))
      .willAnswer(inv -> byDate.get(LocalDate.parse(inv.getArgument(1))));
    given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any()))
      .willReturn(StrategyResponse.builder().strategyType(StrategyType.DCA).build());

    dcaStrategy.executeDca(request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StrategyTransaction>> txCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<StrategyCalculationResult> calcCaptor =
        ArgumentCaptor.forClass(StrategyCalculationResult.class);
    verify(responseMapper).toStrategyResponse(
        any(DcaStrategyRequest.class), txCaptor.capture(), calcCaptor.capture(), any());

    StrategyCalculationResult calc = calcCaptor.getValue();
    List<StrategyTransaction> txs = txCaptor.getValue();

    // 매수 49 + 배당 재투자 16
    assertThat(txs).hasSize(65);
    assertThat(calc.getTotalInvested()).isEqualByComparingTo("4900000");
    assertThat(calc.getTotalShares().doubleValue()).isCloseTo(46.9543, within(0.01));
    assertThat(calc.getDividendsReinvested().doubleValue()).isCloseTo(103.33, within(0.5));
    assertThat(calc.getTotalReturnPercent().doubleValue()).isCloseTo(96.77, within(0.1));

    // 분할 직전 매수(2020-08-01 → 07-31 봉)와 직후 매수(09-01)가 같은 척도다.
    // close 가 조정 안 된 값이면 직전이 425 대로 나와 분할일이 폭락처럼 보인다.
    BigDecimal beforeSplit = priceOn(txs, LocalDate.of(2020, 8, 1));
    BigDecimal afterSplit = priceOn(txs, LocalDate.of(2020, 9, 1));
    assertThat(beforeSplit.doubleValue()).isCloseTo(106.26, within(0.01));
    assertThat(afterSplit.doubleValue()).isCloseTo(134.18, within(0.01));
  }

  private static BigDecimal priceOn(List<StrategyTransaction> txs, LocalDate date) {
    return txs.stream()
        .filter(t -> t.getDate().equals(date))
        .findFirst()
        .orElseThrow(() -> new AssertionError("거래 없음: " + date))
        .getPrice();
  }
}
