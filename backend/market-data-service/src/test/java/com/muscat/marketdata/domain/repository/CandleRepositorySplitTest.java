package com.muscat.marketdata.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("분할 종목 조회 통합 테스트")
class CandleRepositorySplitTest {

  @Autowired
  private CandleRepository candleRepository;

  @Autowired
  private AssetRepository assetRepository;

  @MockBean
  private AssetEventProducer assetEventProducer;

  @MockBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @BeforeEach
  void setUp() {
    candleRepository.deleteAll();
    assetRepository.deleteAll();
  }

  @AfterEach
  void tearDown() {
    candleRepository.deleteAll();
    assetRepository.deleteAll();
  }

  private void asset(String symbol, boolean active) {
    Asset asset = Asset.builder()
      .symbol(symbol).name(symbol).country("US").currency("USD").assetType("EQUITY")
      .build();
    asset.setActive(active);
    assetRepository.save(asset);
  }

  private void candle(String symbol, LocalDate date, String coefficient) {
    candleRepository.save(Candle.builder()
      .symbol(symbol).date(date).currency("USD")
      .open(BigDecimal.TEN).high(BigDecimal.TEN).low(BigDecimal.TEN).close(BigDecimal.TEN)
      .adjustedClose(BigDecimal.TEN).volume(1L).dividendAmount(BigDecimal.ZERO)
      .splitCoefficient(new BigDecimal(coefficient))
      .build());
  }

  @Test
  @DisplayName("활성 종목 중 시작일 이후 분할 계수가 기록된 종목을 한 번씩 이름순으로 준다")
  void 분할_종목만() {
    asset("NVDA", true);
    asset("AAPL", true);
    asset("MSFT", true);
    asset("KO", true);
    asset("ADTX", false);
    candle("NVDA", LocalDate.of(2024, 6, 10), "10");
    candle("AAPL", LocalDate.of(2014, 6, 9), "7");
    candle("AAPL", LocalDate.of(2020, 8, 28), "1");
    candle("AAPL", LocalDate.of(2020, 8, 31), "4");
    candle("MSFT", LocalDate.of(2003, 2, 18), "2");   // 시작일 이전
    candle("KO", LocalDate.of(2020, 1, 2), "1");      // 계수 1 뿐
    candle("ADTX", LocalDate.of(2023, 1, 3), "0.02"); // 비활성

    List<String> result = candleRepository.findSymbolsWithSplits(LocalDate.of(2014, 1, 1));

    assertThat(result).containsExactly("AAPL", "NVDA");
  }
}
