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
}
