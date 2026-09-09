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
 * 야후 실데이터로 DCA 전체 경로를 검증한다.
 * 기대값은 같은 데이터로 따로 계산했다 — 분할 조정 close 로 매수하고 배당은 락일 종가로 재투자한다.
 * 같은 데이터를 adjustedClose 로 계산하면 13.86% 가 나온다. 야후가 준 계열이라 우리 계산과
 * 독립이고, 두 방식이 0.01%p 안에서 만나는 것이 기대값의 근거다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DCA 실데이터 회귀 (KO 2022-09 ~ 2025-09)")
class DCARealDataTest {

  private static final String SYMBOL = "KO";
  private static final BigDecimal FX = new BigDecimal("1300");

  @Mock
  private MarketDataClient marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  @InjectMocks
  private DCAStrategy dcaStrategy;

  private static final List<OHLCPriceDto> BARS = List.of(
      bar("2022-09-01", "62.0000", "55.1078"),
      bar("2022-09-15", "59.5300", "53.2982"),
      bar("2022-09-30", "56.0200", "50.1556"),
      bar("2022-11-01", "59.6400", "53.3967"),
      bar("2022-11-30", "63.6100", "57.3550"),
      bar("2022-12-01", "63.7900", "57.5173"),
      bar("2022-12-30", "63.6100", "57.3550"),
      bar("2023-02-01", "61.3300", "55.2992"),
      bar("2023-03-01", "58.8600", "53.0721"),
      bar("2023-03-16", "60.3000", "54.7875"),
      bar("2023-03-31", "62.0300", "56.3594"),
      bar("2023-05-01", "64.3000", "58.4219"),
      bar("2023-06-01", "60.0000", "54.5150"),
      bar("2023-06-15", "61.2300", "56.0562"),
      bar("2023-06-30", "60.2200", "55.1315"),
      bar("2023-08-01", "61.7700", "56.5506"),
      bar("2023-09-01", "59.3100", "54.2984"),
      bar("2023-09-14", "58.4600", "53.9449"),
      bar("2023-09-29", "55.9800", "51.6564"),
      bar("2023-11-01", "56.4400", "52.0809"),
      bar("2023-11-30", "58.4400", "54.3558"),
      bar("2023-12-01", "58.6400", "54.5418"),
      bar("2023-12-29", "58.9300", "54.8116"),
      bar("2024-02-01", "60.9800", "56.7183"),
      bar("2024-03-01", "59.5300", "55.3696"),
      bar("2024-03-14", "60.5000", "56.7220"),
      bar("2024-04-01", "60.6800", "56.8907"),
      bar("2024-05-01", "61.9300", "58.0627"),
      bar("2024-05-31", "62.9300", "59.0002"),
      bar("2024-06-14", "62.5500", "59.0990"),
      bar("2024-07-01", "63.2800", "59.7887"),
      bar("2024-08-01", "67.9600", "64.2105"),
      bar("2024-08-30", "72.4700", "68.4717"),
      bar("2024-09-13", "71.4100", "67.9327"),
      bar("2024-10-01", "71.7100", "68.2181"),
      bar("2024-11-01", "65.0100", "61.8443"),
      bar("2024-11-29", "64.0800", "61.4220"),
      bar("2024-12-31", "62.2600", "59.6775"),
      bar("2025-01-31", "63.4800", "60.8469"),
      bar("2025-02-28", "71.2100", "68.2562"),
      bar("2025-03-14", "69.1600", "66.7805"),
      bar("2025-04-01", "71.8700", "69.3972"),
      bar("2025-05-01", "71.2900", "68.8372"),
      bar("2025-05-30", "72.1000", "69.6193"),
      bar("2025-06-13", "71.0200", "69.0640"),
      bar("2025-07-01", "71.6700", "69.6961"),
      bar("2025-08-01", "68.8600", "66.9635"),
      bar("2025-08-29", "68.9900", "67.0900")
  );

  private static final List<DividendDto> DIVIDENDS = List.of(
      div("2022-09-15", "0.44"),
      div("2022-11-30", "0.44"),
      div("2023-03-16", "0.46"),
      div("2023-06-15", "0.46"),
      div("2023-09-14", "0.46"),
      div("2023-11-30", "0.46"),
      div("2024-03-14", "0.485"),
      div("2024-06-14", "0.485"),
      div("2024-09-13", "0.485"),
      div("2024-11-29", "0.485"),
      div("2025-03-14", "0.51"),
      div("2025-06-13", "0.51")
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
  @DisplayName("매월 10만원 3년, 배당 재투자 켜고 세금 0")
  void executeDca_realData() {
    DcaStrategyRequest request = DcaStrategyRequest.builder()
      .userId("test-user")
      .symbol(SYMBOL)
      .startDate(LocalDate.of(2022, 9, 1))
      .endDate(LocalDate.of(2025, 9, 1))
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

    // 매수 37 + 배당 재투자 12
    assertThat(txCaptor.getValue()).hasSize(49);
    assertThat(calc.getTotalInvested()).isEqualByComparingTo("3700000");
    assertThat(calc.getTotalShares().doubleValue()).isCloseTo(46.9666, within(0.01));
    assertThat(calc.getDividendsReinvested().doubleValue()).isCloseTo(129.82, within(0.5));
    assertThat(calc.getTotalReturnPercent().doubleValue()).isCloseTo(13.85, within(0.1));

    // 재투자를 했으므로 배당을 자산에 또 더하지 않는다
    assertThat(calc.getTotalAssetKrw()).isEqualByComparingTo(calc.getCurrentValueKrw());
  }

  @Test
  @DisplayName("원천징수 15.4% 를 물리면 재투자액이 줄고 수익률도 낮아진다")
  void executeDca_realData_withTax() {
    DcaStrategyRequest request = DcaStrategyRequest.builder()
      .userId("test-user")
      .symbol(SYMBOL)
      .startDate(LocalDate.of(2022, 9, 1))
      .endDate(LocalDate.of(2025, 9, 1))
      .monthlyAmount(new BigDecimal("100000"))
      .purchaseDay(1)
      .investmentInterval(1)
      .purchaseFxRate(FX)
      .currentFxRate(FX)
      .reinvestDividends(true)
      .dividendTaxRate(new BigDecimal("0.154"))
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

    ArgumentCaptor<StrategyCalculationResult> calcCaptor =
        ArgumentCaptor.forClass(StrategyCalculationResult.class);
    verify(responseMapper).toStrategyResponse(
        any(DcaStrategyRequest.class), any(), calcCaptor.capture(), any());

    StrategyCalculationResult calc = calcCaptor.getValue();

    assertThat(calc.getTotalShares().doubleValue()).isCloseTo(46.6527, within(0.01));
    assertThat(calc.getDividendsReinvested().doubleValue()).isCloseTo(109.37, within(0.5));
    assertThat(calc.getTotalReturnPercent().doubleValue()).isCloseTo(13.08, within(0.1));

    // 세후 재투자액이 세전의 84.6% 보다 낮다. 재투자 주식이 줄어 다음 배당 기준도 같이 줄기 때문
    assertThat(calc.getDividendsReinvested().doubleValue()).isLessThan(129.82 * 0.846);
  }
}
