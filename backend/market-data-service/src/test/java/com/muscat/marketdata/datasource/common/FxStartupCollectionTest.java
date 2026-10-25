package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.marketdata.config.AsyncConfig;
import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("시작 때 환율 채우기")
class FxStartupCollectionTest {

  @Test
  @DisplayName("앱 준비 이벤트는 환율 받기를 기다리지 않고, 채우기는 다른 스레드에서 돈다")
  void readyEventDoesNotWaitForFxFill() {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    FxSource source = date -> {
      started.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return Optional.of(new BigDecimal("1350"));
    };
    FxRateRepository repository = mock(FxRateRepository.class);
    given(repository.findByDateBetween(any(), any())).willReturn(List.of());
    MarketDataProperties properties = new MarketDataProperties();
    properties.getFxIngest().getIncremental().setEnabled(true);
    properties.getFxIngest().setStartDate(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(7));

    new ApplicationContextRunner()
      .withUserConfiguration(AsyncConfig.class)
      .withBean(FxSource.class, () -> source)
      .withBean(FxRateRepository.class, () -> repository)
      .withBean(MarketDataProperties.class, () -> properties)
      .withBean(FxDataCollector.class)
      .run(context -> {
        long begin = System.nanoTime();
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
          context.getSourceApplicationContext(), Duration.ZERO));
        long publishMillis = (System.nanoTime() - begin) / 1_000_000;

        assertThat(started.await(5, TimeUnit.SECONDS)).as("채우기 시작").isTrue();
        assertThat(publishMillis).as("이벤트가 채우기를 기다린 시간").isLessThan(2_000);
        release.countDown();
      });
  }
}
