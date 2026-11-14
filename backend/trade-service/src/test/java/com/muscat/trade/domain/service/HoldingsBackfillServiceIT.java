package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Flyway 로 만든 Postgres 스키마에서 체결 시각 순서로 쌓인 보유를 거래일 순서로 다시 쌓는 백필 확인
@DataJpaTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@Import({HoldingsBackfillService.class, HoldingsRebuilder.class, HoldingsBackfillServiceIT.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DisplayName("보유 다시 쌓기 백필 통합 테스트 (TestContainers)")
class HoldingsBackfillServiceIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);
  private static final LocalDate PAST = LocalDate.of(2025, 6, 2);

  @TestConfiguration
  static class Beans {
    @Bean
    TradeProperties tradeProperties() {
      return new TradeProperties();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @MockitoBean
  private TradeLogger tradeLogger;

  @Autowired
  private HoldingsBackfillService holdingsBackfillService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private HoldingsRepository holdingsRepository;

  @AfterEach
  void tearDown() {
    tradeRepository.deleteAllInBatch();
    holdingsRepository.deleteAllInBatch();
  }

  private Trade completed(long accountId, TradeType type, String quantity, String price, LocalDate tradeDate,
    LocalDateTime executedAt) {
    BigDecimal amount = new BigDecimal(quantity).multiply(new BigDecimal(price));
    return tradeRepository.save(Trade.builder().userId("user-1").accountId(accountId).symbol("AAPL")
      .tradeType(type).quantity(new BigDecimal(quantity)).price(new BigDecimal(price)).totalAmount(amount)
      .tradeDate(tradeDate).executedAt(executedAt).status(TradeStatus.COMPLETED).build());
  }

  private void holdings(long accountId, String quantity, String avgPrice) {
    holdingsRepository.save(Holdings.builder().userId("user-1").accountId(accountId).symbol("AAPL")
      .totalQuantity(new BigDecimal(quantity)).avgPurchasePrice(new BigDecimal(avgPrice))
      .totalInvestedAmount(new BigDecimal(quantity).multiply(new BigDecimal(avgPrice))).build());
  }

  private Holdings holdingsOf(long accountId) {
    return holdingsRepository.findByUserIdAndAccountIdAndSymbol("user-1", accountId, "AAPL").orElseThrow();
  }

  @Test
  @DisplayName("체결 시각 순서로 쌓인 보유를 거래일 순서 평균 단가로 바꾸고 바뀐 행 수만 셈")
  void rebuildAll_RebuildsInTradeDateOrder() {
    // 오늘 매수 @100 → 오늘 매도 @110 → 과거 날짜 매수 @20(체결 시각 순서면 10주 @20, 거래일 순서면 10주 @60)
    completed(20L, TradeType.BUY, "10", "100.00", TODAY, LocalDateTime.of(2025, 6, 30, 10, 0));
    completed(20L, TradeType.SELL, "10", "110.00", TODAY, LocalDateTime.of(2025, 6, 30, 11, 0));
    completed(20L, TradeType.BUY, "10", "20.00", PAST, LocalDateTime.of(2025, 6, 30, 12, 0));
    holdings(20L, "10", "20.00");
    // 이미 거래일 순서와 같은 계좌
    completed(21L, TradeType.BUY, "5", "10.00", PAST, LocalDateTime.of(2025, 6, 30, 9, 0));
    holdings(21L, "5", "10.00");

    assertThat(holdingsBackfillService.rebuildAll()).isEqualTo(1);

    assertThat(holdingsOf(20L).getTotalQuantity()).isEqualByComparingTo("10");
    assertThat(holdingsOf(20L).getAvgPurchasePrice()).isEqualByComparingTo("60.00");
    assertThat(holdingsOf(21L).getAvgPurchasePrice()).isEqualByComparingTo("10.00");
    assertThat(holdingsBackfillService.rebuildAll()).isZero();
  }
}
