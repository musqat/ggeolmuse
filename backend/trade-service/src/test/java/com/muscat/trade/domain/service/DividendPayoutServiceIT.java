package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.kafka.DividendPayoutProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

// Flyway 로 만든 Postgres 표에서 몫 표의 키 · 갱신과 계좌 사이 분리를 봄
@SpringBootTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("배당 몫 재계산 통합 테스트 (TestContainers)")
class DividendPayoutServiceIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Container
  static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  private static final String USER = "it-user@example.com";
  private static final long ACCOUNT_A = 301L;
  private static final long ACCOUNT_B = 302L;
  private static final String SYMBOL = "AAPL";

  @Autowired
  private DividendPayoutService dividendPayoutService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private DividendPayoutRepository dividendPayoutRepository;

  @MockitoBean
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @MockitoBean
  private DividendPayoutProducer dividendPayoutProducer;

  @BeforeEach
  void setUp() {
    given(marketServiceClientWrapper.getDividends(eq(SYMBOL), eq("2024-01-02"), anyString())).willReturn(List.of(
      new DividendDto("2024-02-09", new BigDecimal("0.24")),
      new DividendDto("2025-05-12", new BigDecimal("0.25")),
      new DividendDto("2026-08-11", new BigDecimal("0.26"))));
  }

  @AfterEach
  void tearDown() {
    dividendPayoutRepository.deleteAll();
    tradeRepository.deleteAll();
  }

  private void saveTrade(long accountId, TradeType type, LocalDate tradeDate) {
    tradeRepository.save(Trade.builder()
      .userId(USER).accountId(accountId).symbol(SYMBOL).tradeType(type)
      .quantity(new BigDecimal("10")).price(new BigDecimal("180")).totalAmount(new BigDecimal("1800"))
      .tradeDate(tradeDate).executedAt(tradeDate.atTime(10, 0))
      .build());
  }

  private List<String> netAmounts(long accountId) {
    return dividendPayoutRepository.findByAccountIdAndSymbol(accountId, SYMBOL).stream()
      .sorted(Comparator.comparing(DividendPayout::getExDate))
      .map(p -> p.getNetAmount().toPlainString())
      .toList();
  }

  @Test
  @DisplayName("같은 사용자의 두 계좌가 같은 종목을 가져도 한 계좌의 과거 매도가 다른 계좌 몫을 바꾸지 않는다")
  void reconcile_OtherAccountUnaffected() {
    saveTrade(ACCOUNT_A, TradeType.BUY, LocalDate.of(2024, 1, 2));
    saveTrade(ACCOUNT_B, TradeType.BUY, LocalDate.of(2024, 1, 2));
    dividendPayoutService.reconcile(USER, ACCOUNT_A, SYMBOL);
    dividendPayoutService.reconcile(USER, ACCOUNT_B, SYMBOL);

    saveTrade(ACCOUNT_B, TradeType.SELL, LocalDate.of(2025, 3, 10));
    int changed = dividendPayoutService.reconcile(USER, ACCOUNT_B, SYMBOL);

    assertThat(changed).isEqualTo(2);
    assertThat(netAmounts(ACCOUNT_A)).containsExactly("2.03", "2.11", "2.20");
    assertThat(netAmounts(ACCOUNT_B)).containsExactly("2.03", "0.00", "0.00");
    assertThat(dividendPayoutRepository.findAll()).hasSize(6);
  }

  @Test
  @DisplayName("주당 배당이 소수 여덟째 자리까지여도 저장한 몫과 같아 다시 계산할 때 바뀌지 않는다")
  void reconcile_EightDecimalDividend_StaysUnchanged() {
    given(marketServiceClientWrapper.getDividends(eq(SYMBOL), eq("2024-01-02"), anyString())).willReturn(List.of(
      new DividendDto("2024-02-09", new BigDecimal("0.24666667"))));
    saveTrade(ACCOUNT_A, TradeType.BUY, LocalDate.of(2024, 1, 2));
    dividendPayoutService.reconcile(USER, ACCOUNT_A, SYMBOL);

    assertThat(dividendPayoutService.reconcile(USER, ACCOUNT_A, SYMBOL)).isZero();
  }
}
