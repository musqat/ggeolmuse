package com.muscat.marketdata.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("환율 최저 · 최고 조회 통합 테스트")
class FxRateRepositoryRangeTest {

  @Autowired
  private FxRateRepository fxRateRepository;

  @MockitoBean
  private AssetEventProducer assetEventProducer;

  @MockitoBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @BeforeEach
  void setUp() {
    fxRateRepository.deleteAll();
  }

  @AfterEach
  void tearDown() {
    fxRateRepository.deleteAll();
  }

  private void rate(LocalDate date, String rate) {
    fxRateRepository.save(FxRate.builder().date(date).rate(new BigDecimal(rate)).build());
  }

  @Test
  @DisplayName("날짜 순서와 상관없이 환율 값이 가장 낮은 날 · 높은 날을 돌려준다")
  void findsLowestAndHighestRate() {
    rate(LocalDate.of(2026, 7, 2), "1554.40");
    rate(LocalDate.of(2018, 4, 3), "1053.37");
    rate(LocalDate.of(2026, 10, 9), "1339.20");

    assertThat(fxRateRepository.findFirstByOrderByRateAsc()).get()
      .satisfies(fx -> assertThat(fx.getDate()).isEqualTo(LocalDate.of(2018, 4, 3)));
    assertThat(fxRateRepository.findFirstByOrderByRateDesc()).get()
      .satisfies(fx -> assertThat(fx.getDate()).isEqualTo(LocalDate.of(2026, 7, 2)));
  }

  @Test
  @DisplayName("환율 데이터가 없으면 비어 있다")
  void emptyWhenNoRates() {
    assertThat(fxRateRepository.findFirstByOrderByRateAsc()).isEmpty();
    assertThat(fxRateRepository.findFirstByOrderByRateDesc()).isEmpty();
  }
}
