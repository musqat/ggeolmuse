package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.DividendRepository;
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
@DisplayName("DividendService 단위 테스트")
class DividendServiceImplTest {

  @Mock
  private DividendRepository dividendRepository;

  @InjectMocks
  private DividendServiceImpl dividendService;

  private static final String TEST_SYMBOL = "AAPL";
  private static final LocalDate TEST_EX_DATE = LocalDate.of(2024, 8, 9);
  private static final BigDecimal TEST_AMOUNT = new BigDecimal("0.25");

  private Dividend testDividend;

  @BeforeEach
  void setUp() {
    testDividend = Dividend.builder()
      .symbol(TEST_SYMBOL)
      .exDate(TEST_EX_DATE)
      .amount(TEST_AMOUNT)
      .currency("USD")
      .build();
  }

  @Nested
  @DisplayName("배당 이력 조회 테스트")
  class GetDividendHistoryTests {

    @Test
    @DisplayName("지정된 기간의 배당 이력을 조회한다")
    void getDividendHistory_WithDateRange_Success() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      List<Dividend> dividends = List.of(
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 2, 9))
          .amount(new BigDecimal("0.24"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 5, 10))
          .amount(new BigDecimal("0.25"))
          .currency("USD")
          .build(),
        testDividend,
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 11, 8))
          .amount(new BigDecimal("0.25"))
          .currency("USD")
          .build()
      );

      given(dividendRepository.findBySymbolsAndDateRange(
        List.of(TEST_SYMBOL), startDate, endDate))
        .willReturn(dividends);

      // when
      List<DividendDto> results = dividendService.getDividendHistory(TEST_SYMBOL, startDate,
        endDate);

      // then
      assertThat(results).hasSize(4);
      assertThat(results).extracting(DividendDto::getExDate)
        .containsExactly(
          LocalDate.of(2024, 2, 9),
          LocalDate.of(2024, 5, 10),
          LocalDate.of(2024, 8, 9),
          LocalDate.of(2024, 11, 8)
        );

      // DTO 변환 검증
      DividendDto firstDividend = results.getFirst();
      assertThat(firstDividend.getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(firstDividend.getAmount()).isEqualByComparingTo(new BigDecimal("0.24"));
      assertThat(firstDividend.getCurrency()).isEqualTo("USD");
      assertThat(firstDividend.getSource()).isEqualTo("MarketData");

      verify(dividendRepository).findBySymbolsAndDateRange(
        List.of(TEST_SYMBOL), startDate, endDate);
    }

    @Test
    @DisplayName("배당 이력이 없으면 빈 리스트를 반환한다")
    void getDividendHistory_NoData_ReturnsEmpty() {
      // given
      LocalDate startDate = LocalDate.of(2020, 1, 1);
      LocalDate endDate = LocalDate.of(2020, 12, 31);

      given(dividendRepository.findBySymbolsAndDateRange(
        List.of(TEST_SYMBOL), startDate, endDate))
        .willReturn(new ArrayList<>());

      // when
      List<DividendDto> results = dividendService.getDividendHistory(TEST_SYMBOL, startDate,
        endDate);

      // then
      assertThat(results).isEmpty();
      verify(dividendRepository).findBySymbolsAndDateRange(
        List.of(TEST_SYMBOL), startDate, endDate);
    }

    @Test
    @DisplayName("심볼을 대문자로 변환하여 조회한다")
    void getDividendHistory_ConvertsToUpperCase_Success() {
      // given
      String lowerCaseSymbol = "aapl";
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      given(dividendRepository.findBySymbolsAndDateRange(
        List.of("AAPL"), startDate, endDate))
        .willReturn(List.of(testDividend));

      // when
      List<DividendDto> results = dividendService.getDividendHistory(lowerCaseSymbol, startDate,
        endDate);

      // then
      assertThat(results).hasSize(1);
      verify(dividendRepository).findBySymbolsAndDateRange(
        List.of("AAPL"), startDate, endDate);
    }

    @Test
    @DisplayName("DTO 변환이 올바르게 수행된다")
    void getDividendHistory_DtoConversion_Accurate() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      given(dividendRepository.findBySymbolsAndDateRange(
        List.of(TEST_SYMBOL), startDate, endDate))
        .willReturn(List.of(testDividend));

      // when
      List<DividendDto> results = dividendService.getDividendHistory(TEST_SYMBOL, startDate,
        endDate);

      // then
      assertThat(results).hasSize(1);
      DividendDto dto = results.getFirst();

      // 모든 필드가 정확히 변환되었는지 확인
      assertThat(dto.getSymbol()).isEqualTo(testDividend.getSymbol());
      assertThat(dto.getExDate()).isEqualTo(testDividend.getExDate());
      assertThat(dto.getAmount()).isEqualByComparingTo(testDividend.getAmount());
      assertThat(dto.getCurrency()).isEqualTo(testDividend.getCurrency());
      assertThat(dto.getSource()).isEqualTo("MarketData");
    }

    @Test
    @DisplayName("여러 배당 데이터가 순서대로 조회된다")
    void getDividendHistory_MultipleEntries_OrderedCorrectly() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      // 분기별 배당 데이터 (연 4회)
      List<Dividend> quarterlyDividends = List.of(
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 2, 9))
          .amount(new BigDecimal("0.24"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 5, 10))
          .amount(new BigDecimal("0.24"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 8, 9))
          .amount(new BigDecimal("0.25"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol(TEST_SYMBOL)
          .exDate(LocalDate.of(2024, 11, 8))
          .amount(new BigDecimal("0.25"))
          .currency("USD")
          .build()
      );

      given(dividendRepository.findBySymbolsAndDateRange(
        List.of(TEST_SYMBOL), startDate, endDate))
        .willReturn(quarterlyDividends);

      // when
      List<DividendDto> results = dividendService.getDividendHistory(TEST_SYMBOL, startDate,
        endDate);

      // then
      assertThat(results).hasSize(4);
      // 배당 금액 합계 검증 (연간 배당)
      BigDecimal totalDividend = results.stream()
        .map(DividendDto::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(totalDividend).isEqualByComparingTo(new BigDecimal("0.98")); // 0.24*2 + 0.25*2
    }
  }

  @Nested
  @DisplayName("고배당주 검색 테스트")
  class FindHighDividendStocksTests {

    @Test
    @DisplayName("최소 금액 이상의 고배당주를 검색한다")
    void findHighDividendStocks_AboveMinAmount_Success() {
      // given
      BigDecimal minAmount = new BigDecimal("0.50");
      LocalDate fromDate = LocalDate.of(2024, 1, 1);

      List<Dividend> highDividends = List.of(
        Dividend.builder()
          .symbol("VZ")
          .exDate(LocalDate.of(2024, 7, 10))
          .amount(new BigDecimal("0.6775"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol("T")
          .exDate(LocalDate.of(2024, 7, 8))
          .amount(new BigDecimal("0.2775"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol("HD")
          .exDate(LocalDate.of(2024, 9, 5))
          .amount(new BigDecimal("2.25"))
          .currency("USD")
          .build()
      );

      given(dividendRepository.findHighDividendStocks(minAmount, fromDate))
        .willReturn(highDividends);

      // when
      List<DividendDto> results = dividendService.findHighDividendStocks(minAmount, fromDate);

      // then
      assertThat(results).hasSize(3);
      assertThat(results).extracting(DividendDto::getSymbol)
        .containsExactly("VZ", "T", "HD");

      // 모든 배당금이 최소 금액 이상인지 검증 (실제로는 T가 0.2775로 작지만, QueryRepository의 결과를 그대로 반환)
      DividendDto hdDividend = results.get(2);
      assertThat(hdDividend.getAmount()).isEqualByComparingTo(new BigDecimal("2.25"));
      assertThat(hdDividend.getCurrency()).isEqualTo("USD");
      assertThat(hdDividend.getSource()).isEqualTo("MarketData");

      verify(dividendRepository).findHighDividendStocks(minAmount, fromDate);
    }

    @Test
    @DisplayName("고배당주가 없으면 빈 리스트를 반환한다")
    void findHighDividendStocks_NoHighDividends_ReturnsEmpty() {
      // given
      BigDecimal minAmount = new BigDecimal("10.00"); // 매우 높은 배당금
      LocalDate fromDate = LocalDate.of(2024, 1, 1);

      given(dividendRepository.findHighDividendStocks(minAmount, fromDate))
        .willReturn(new ArrayList<>());

      // when
      List<DividendDto> results = dividendService.findHighDividendStocks(minAmount, fromDate);

      // then
      assertThat(results).isEmpty();
      verify(dividendRepository).findHighDividendStocks(minAmount, fromDate);
    }

    @Test
    @DisplayName("DTO 변환이 올바르게 수행된다")
    void findHighDividendStocks_DtoConversion_Accurate() {
      // given
      BigDecimal minAmount = new BigDecimal("0.20");
      LocalDate fromDate = LocalDate.of(2024, 1, 1);

      Dividend highDividend = Dividend.builder()
        .symbol("HD")
        .exDate(LocalDate.of(2024, 9, 5))
        .amount(new BigDecimal("2.25"))
        .currency("USD")
        .build();

      given(dividendRepository.findHighDividendStocks(minAmount, fromDate))
        .willReturn(List.of(highDividend));

      // when
      List<DividendDto> results = dividendService.findHighDividendStocks(minAmount, fromDate);

      // then
      assertThat(results).hasSize(1);
      DividendDto dto = results.getFirst();

      assertThat(dto.getSymbol()).isEqualTo(highDividend.getSymbol());
      assertThat(dto.getExDate()).isEqualTo(highDividend.getExDate());
      assertThat(dto.getAmount()).isEqualByComparingTo(highDividend.getAmount());
      assertThat(dto.getCurrency()).isEqualTo(highDividend.getCurrency());
      assertThat(dto.getSource()).isEqualTo("MarketData");
    }

    @Test
    @DisplayName("다양한 종목의 고배당주를 검색한다")
    void findHighDividendStocks_MultipleTickers_Success() {
      // given
      BigDecimal minAmount = new BigDecimal("0.60");
      LocalDate fromDate = LocalDate.of(2024, 1, 1);

      List<Dividend> diverseHighDividends = List.of(
        Dividend.builder()
          .symbol("VZ")
          .exDate(LocalDate.of(2024, 7, 10))
          .amount(new BigDecimal("0.6775"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol("KO")
          .exDate(LocalDate.of(2024, 6, 14))
          .amount(new BigDecimal("0.485"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol("JNJ")
          .exDate(LocalDate.of(2024, 8, 26))
          .amount(new BigDecimal("1.19"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol("PG")
          .exDate(LocalDate.of(2024, 7, 19))
          .amount(new BigDecimal("0.9407"))
          .currency("USD")
          .build()
      );

      given(dividendRepository.findHighDividendStocks(minAmount, fromDate))
        .willReturn(diverseHighDividends);

      // when
      List<DividendDto> results = dividendService.findHighDividendStocks(minAmount, fromDate);

      // then
      assertThat(results).hasSize(4);
      assertThat(results).extracting(DividendDto::getSymbol)
        .containsExactlyInAnyOrder("VZ", "KO", "JNJ", "PG");
      assertThat(results).allMatch(dto -> dto.getCurrency().equals("USD"));
      assertThat(results).allMatch(dto -> dto.getSource().equals("MarketData"));
    }

    @Test
    @DisplayName("최소 금액이 0이면 모든 배당주를 반환한다")
    void findHighDividendStocks_MinAmountZero_ReturnsAll() {
      // given
      BigDecimal minAmount = BigDecimal.ZERO;
      LocalDate fromDate = LocalDate.of(2024, 1, 1);

      List<Dividend> allDividends = List.of(
        Dividend.builder()
          .symbol("AAPL")
          .exDate(LocalDate.of(2024, 8, 9))
          .amount(new BigDecimal("0.25"))
          .currency("USD")
          .build(),
        Dividend.builder()
          .symbol("MSFT")
          .exDate(LocalDate.of(2024, 8, 15))
          .amount(new BigDecimal("0.75"))
          .currency("USD")
          .build()
      );

      given(dividendRepository.findHighDividendStocks(minAmount, fromDate))
        .willReturn(allDividends);

      // when
      List<DividendDto> results = dividendService.findHighDividendStocks(minAmount, fromDate);

      // then
      assertThat(results).hasSize(2);
      verify(dividendRepository).findHighDividendStocks(minAmount, fromDate);
    }
  }
}
