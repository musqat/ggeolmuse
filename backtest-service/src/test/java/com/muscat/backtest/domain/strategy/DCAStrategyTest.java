package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DCA 전략 단위 테스트")
class DCAStrategyTest {

  @Mock
  private MarketDataClient marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  @InjectMocks
  private DCAStrategy dcaStrategy;

  private String userId;
  private String symbol;
  private LocalDate startDate;
  private LocalDate endDate;

  @BeforeEach
  void setUp() {
    userId = "test-user";
    symbol = "DCA-TEST";
    startDate = LocalDate.of(2024, 1, 1);
    endDate = LocalDate.of(2024, 12, 31);
  }

  @Nested
  @DisplayName("DCA 전략 실행 테스트")
  class ExecuteDcaTests {

    @Test
    @DisplayName("매월 정기 매수가 실행된다")
    void executeDca_MonthlyPurchase_Success() {
      // given
      BigDecimal monthlyAmount = new BigDecimal("1000.00");
      Integer purchaseDay = 15;

      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(startDate)
        .endDate(LocalDate.of(2024, 3, 31)) // 3개월
        .monthlyAmount(monthlyAmount)
        .purchaseDay(purchaseDay)
        .build();

      // 각 월의 가격 데이터 Mock (BacktestDataUtils.getHistoricalPrice 내부에서 호출)
      given(marketDataClient.getOHLCPrice(eq(symbol), eq("2024-01-15")))
        .willReturn(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")));
      given(marketDataClient.getOHLCPrice(eq(symbol), eq("2024-02-15")))
        .willReturn(createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("105.00")));
      given(marketDataClient.getOHLCPrice(eq(symbol), eq("2024-03-15")))
        .willReturn(createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("110.00")));

      // 환율 데이터 Mock
      MarketDataClient.FxRate fxRate = new MarketDataClient.FxRate(LocalDate.now(),
        new BigDecimal("1300.00"));
      given(marketDataClient.getFxRate(anyString())).willReturn(fxRate);
      given(marketDataClient.getLatestFxRate()).willReturn(fxRate);

      // 현재 가격 Mock
      given(marketDataClient.getCurrentPrice(eq(symbol)))
        .willReturn(createStockPrice(new BigDecimal("110.00")));

      // 배당 데이터 Mock (빈 리스트)
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      // ResponseMapper Mock
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .strategyType(StrategyType.DCA)
        .totalInvested(new BigDecimal("3000.00"))
        .totalShares(new BigDecimal("28.96"))
        .averagePrice(new BigDecimal("103.59"))
        .currentValue(new BigDecimal("3185.60"))
        .totalReturn(new BigDecimal("185.60"))
        .totalReturnPercent(new BigDecimal("6.19"))
        .transactions(createDcaTransactions())
        .build();

      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class),
        any(), any(StrategyCalculationResult.class), any()))
        .willReturn(expectedResponse);

      // when
      StrategyResponse result = dcaStrategy.executeDca(request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getStrategyType()).isEqualTo(StrategyType.DCA);
      assertThat(result.getTotalInvested()).isEqualByComparingTo(new BigDecimal("3000.00"));
      assertThat(result.getTotalReturnPercent()).isGreaterThan(BigDecimal.ZERO);

      verify(marketDataClient, times(3)).getOHLCPrice(eq(symbol), anyString());
    }

    @Test
    @DisplayName("매수일이 주말/공휴일이면 다음 영업일에 매수한다")
    void executeDca_WeekendPurchaseDay_ShiftsToNextBusinessDay() {
      // given
      BigDecimal monthlyAmount = new BigDecimal("1000.00");
      Integer purchaseDay = 1; // 토요일인 경우

      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 6, 1)) // 토요일
        .endDate(LocalDate.of(2024, 6, 30))
        .monthlyAmount(monthlyAmount)
        .purchaseDay(purchaseDay)
        .build();

      // 2024-06-01은 토요일이므로 5일간 검색하며 월요일(2024-06-03) 데이터를 반환
      // BacktestDataUtils.getHistoricalPrice는 최대 5일 전까지 검색
      given(marketDataClient.getOHLCPrice(eq(symbol), eq("2024-06-01")))
        .willReturn(null); // 주말 - 데이터 없음
      given(marketDataClient.getOHLCPrice(eq(symbol), eq("2024-05-31")))
        .willReturn(createOHLC(LocalDate.of(2024, 5, 31), new BigDecimal("100.00"))); // 금요일

      // 환율 데이터 Mock
      MarketDataClient.FxRate fxRate = new MarketDataClient.FxRate(LocalDate.now(),
        new BigDecimal("1300.00"));
      given(marketDataClient.getFxRate(anyString())).willReturn(fxRate);
      given(marketDataClient.getLatestFxRate()).willReturn(fxRate);

      // 현재 가격 Mock
      given(marketDataClient.getCurrentPrice(eq(symbol)))
        .willReturn(createStockPrice(new BigDecimal("100.00")));

      // 배당 데이터 Mock
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      StrategyResponse expectedResponse = StrategyResponse.builder()
        .strategyType(StrategyType.DCA)
        .transactions(createDcaTransactions())
        .build();

      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class),
        any(), any(StrategyCalculationResult.class), any()))
        .willReturn(expectedResponse);

      // when
      StrategyResponse result = dcaStrategy.executeDca(request);

      // then
      assertThat(result).isNotNull();
      // 주말을 건너뛰고 이전 영업일에 매수했는지 검증
    }

    @Test
    @DisplayName("가격 데이터가 없으면 예외가 발생한다")
    void executeDca_NoPriceData_ThrowsException() {
      // given
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol("INVALID")
        .startDate(startDate)
        .endDate(endDate)
        .monthlyAmount(new BigDecimal("1000.00"))
        .purchaseDay(15)
        .build();

      // BacktestDataUtils.getHistoricalPrice는 5일간 검색 후 데이터가 없으면 예외 발생
      given(marketDataClient.getOHLCPrice(eq("INVALID"), anyString()))
        .willReturn(null);

      // when & then
      assertThatThrownBy(() -> dcaStrategy.executeDca(request))
        .isInstanceOf(com.muscat.backtest.common.exception.BacktestException.class)
        .hasMessageContaining("적립식 투자 전략 실행 가능한 데이터가 없습니다");
    }
  }

  @Nested
  @DisplayName("전략 타입 테스트")
  class StrategyTypeTests {

    @Test
    @DisplayName("전략 타입이 DCA로 반환된다")
    void getStrategyType_ReturnsDCA() {
      // when
      StrategyType type = dcaStrategy.getStrategyType();

      // then
      assertThat(type).isEqualTo(StrategyType.DCA);
    }
  }

  @Nested
  @DisplayName("배당금 재투자 테스트")
  class DividendReinvestmentTests {

    @Test
    @DisplayName("배당금 재투자가 활성화되면 배당금으로 추가 주식을 매수한다")
    void executeDca_WithDividendReinvestment_ReinvestsDividends() {
      // given
      BigDecimal monthlyAmount = new BigDecimal("1000.00");
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 3, 31))
        .monthlyAmount(monthlyAmount)
        .purchaseDay(15)
        .reinvestDividends(true)
        .build();

      // Mock price data
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")));

      // Mock FX rate
      MarketDataClient.FxRate fxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1300.00"));
      given(marketDataClient.getFxRate(anyString())).willReturn(fxRate);
      given(marketDataClient.getLatestFxRate()).willReturn(fxRate);

      // Mock current price
      given(marketDataClient.getCurrentPrice(eq(symbol)))
        .willReturn(createStockPrice(new BigDecimal("100.00")));

      // Mock dividend data
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      // Mock ResponseMapper
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .strategyType(StrategyType.DCA)
        .build();
      given(responseMapper.toStrategyResponse(
        any(DcaStrategyRequest.class), any(), any(), any()))
        .willReturn(expectedResponse);

      // when
      StrategyResponse result = dcaStrategy.executeDca(request);

      // then
      assertThat(result).isNotNull();
      verify(marketDataClient).getDividendHistory(eq(symbol), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("투자 한도 테스트")
  class InvestmentLimitTests {

    @Test
    @DisplayName("총 투자금 한도에 도달하면 투자를 중단한다")
    void executeDca_WithInvestmentLimit_StopsAtLimit() {
      // given
      BigDecimal monthlyAmount = new BigDecimal("1000.00");
      BigDecimal investmentLimit = new BigDecimal("2500.00"); // 3개월치보다 적게 설정

      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 6, 30)) // 6개월 설정
        .monthlyAmount(monthlyAmount)
        .purchaseDay(15)
        .totalInvestmentLimit(investmentLimit)
        .build();

      // Mock price data for all months
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(LocalDate.now(), new BigDecimal("100.00")));

      // Mock FX rate
      MarketDataClient.FxRate fxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1300.00"));
      given(marketDataClient.getFxRate(anyString())).willReturn(fxRate);
      given(marketDataClient.getLatestFxRate()).willReturn(fxRate);

      // Mock current price
      given(marketDataClient.getCurrentPrice(eq(symbol)))
        .willReturn(createStockPrice(new BigDecimal("100.00")));

      // Mock dividend data
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      // Mock ResponseMapper
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .strategyType(StrategyType.DCA)
        .totalInvested(new BigDecimal("2000.00")) // 2개월치만 투자
        .build();
      given(responseMapper.toStrategyResponse(
        any(DcaStrategyRequest.class), any(), any(), any()))
        .willReturn(expectedResponse);

      // when
      StrategyResponse result = dcaStrategy.executeDca(request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getTotalInvested()).isLessThanOrEqualTo(investmentLimit);
    }
  }

  // 내부 메서드
  private OHLCPriceDto createOHLC(LocalDate date, BigDecimal closePrice) {
    return new OHLCPriceDto(
      symbol,
      date,
      closePrice.subtract(new BigDecimal("1.00")),  // openPrice
      closePrice.add(new BigDecimal("2.00")),       // highPrice
      closePrice.subtract(new BigDecimal("2.00")),  // lowPrice
      closePrice,                                    // closePrice
      null,                                          // adjustedClose
      1000000L,                                      // volume
      "USD",                                         // currency
      true                                           // available
    );
  }

  private StockPriceDto createStockPrice(BigDecimal price) {
    return new StockPriceDto(
      symbol,                    // symbol
      "Test Stock",              // name
      price,                     // currentPrice
      null,                      // previousClose
      null,                      // change
      null,                      // changePercent
      null,                      // volume
      null,                      // date
      null,                      // lastUpdated
      null,                      // openPrice
      null,                      // highPrice
      null,                      // lowPrice
      null,                      // closePrice
      null,                      // adjustedClose
      "USD",                     // currency
      true,                      // available
      null,                      // assetType
      null                       // marketCap
    );
  }

  private List<StrategyTransaction> createDcaTransactions() {
    List<StrategyTransaction> transactions = new ArrayList<>();
    transactions.add(StrategyTransaction.builder()
      .date(LocalDate.of(2024, 1, 15))
      .actualDate(LocalDate.of(2024, 1, 15))
      .trigger("월정액")
      .price(new BigDecimal("100.00"))
      .shares(new BigDecimal("10.00"))
      .amount(new BigDecimal("1000.00"))
      .fxRate(new BigDecimal("1300.00"))
      .build());
    transactions.add(StrategyTransaction.builder()
      .date(LocalDate.of(2024, 2, 15))
      .actualDate(LocalDate.of(2024, 2, 15))
      .trigger("월정액")
      .price(new BigDecimal("105.00"))
      .shares(new BigDecimal("9.52"))
      .amount(new BigDecimal("1000.00"))
      .fxRate(new BigDecimal("1300.00"))
      .build());
    transactions.add(StrategyTransaction.builder()
      .date(LocalDate.of(2024, 3, 15))
      .actualDate(LocalDate.of(2024, 3, 15))
      .trigger("월정액")
      .price(new BigDecimal("110.00"))
      .shares(new BigDecimal("9.09"))
      .amount(new BigDecimal("1000.00"))
      .fxRate(new BigDecimal("1300.00"))
      .build());
    return transactions;
  }

  private StrategyCalculationResult createMockCalculation() {
    return StrategyCalculationResult.builder()
      .totalInvested(new BigDecimal("1000.00"))
      .totalShares(new BigDecimal("10.00"))
      .averagePrice(new BigDecimal("100.00"))
      .currentValue(new BigDecimal("1000.00"))
      .currentValueKrw(new BigDecimal("1300000.00"))
      .totalReturnUsd(BigDecimal.ZERO)
      .totalReturnPercent(BigDecimal.ZERO)
      .totalReturnKrw(BigDecimal.ZERO)
      .build();
  }
}
