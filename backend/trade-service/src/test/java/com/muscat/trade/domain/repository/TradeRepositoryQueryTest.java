package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@DisplayName("체결 레포지토리 - QueryDSL 조회")
class TradeRepositoryQueryTest {

  private static final String USER = "user-1";
  private static final String OTHER_USER = "user-2";

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private TestEntityManager entityManager;

  // 넣은 행을 먼저 내보내고 그 뒤 호출에서 실행된 SQL 수
  private long statementsOf(Runnable call) {
    entityManager.flush();
    Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
      .unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    call.run();
    return statistics.getPrepareStatementCount();
  }

  private Trade save(String userId, long accountId, String symbol, TradeType type, String quantity,
    String totalAmount, LocalDate tradeDate, int hour, TradeStatus status) {
    return tradeRepository.save(Trade.builder()
      .userId(userId).accountId(accountId).symbol(symbol).tradeType(type)
      .quantity(new BigDecimal(quantity)).price(new BigDecimal("10"))
      .totalAmount(new BigDecimal(totalAmount)).fee(BigDecimal.ZERO)
      .tradeDate(tradeDate).executedAt(tradeDate.atTime(hour, 0)).status(status)
      .build());
  }

  private Trade save(String userId, long accountId, String symbol, TradeType type, String quantity,
    String totalAmount, LocalDate tradeDate, int hour) {
    return save(userId, accountId, symbol, type, quantity, totalAmount, tradeDate, hour, TradeStatus.COMPLETED);
  }

  private Trade saveBuy(TradeStatus status, LocalDateTime eventPublishedAt) {
    return tradeRepository.save(Trade.builder()
      .userId(USER).accountId(10L).symbol("AAPL").tradeType(TradeType.BUY)
      .quantity(BigDecimal.ONE).price(new BigDecimal("10")).totalAmount(new BigDecimal("10")).fee(BigDecimal.ZERO)
      .tradeDate(LocalDate.of(2026, 10, 19)).executedAt(LocalDateTime.of(2026, 10, 19, 9, 0))
      .status(status).eventPublishedAt(eventPublishedAt)
      .build());
  }

  @Test
  @DisplayName("발행 시각이 기준보다 앞선 대기 매수만 다시 보낼 대상으로 준다")
  void findStalePendingBuys_PublishedBeforeCutoffOnly() {
    LocalDateTime publishedAt = LocalDateTime.of(2026, 10, 19, 9, 0);
    Trade stale = saveBuy(TradeStatus.PENDING, publishedAt);
    saveBuy(TradeStatus.COMPLETED, publishedAt);
    saveBuy(TradeStatus.PENDING, null);

    assertThat(tradeRepository.findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(
      TradeType.BUY, TradeStatus.PENDING, publishedAt.plusMinutes(5)))
      .extracting(Trade::getId).containsExactly(stale.getId());
    assertThat(tradeRepository.findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(
      TradeType.BUY, TradeStatus.PENDING, publishedAt.minusMinutes(5))).isEmpty();
  }

  @Test
  @DisplayName("기준 시각 전에 만든 대기 매수만 센다")
  void countPendingBuys_BeforeCutoffOnly() {
    saveBuy(TradeStatus.PENDING, null);
    saveBuy(TradeStatus.COMPLETED, null);

    assertThat(tradeRepository.countByTradeTypeAndStatusAndCreatedAtBefore(
      TradeType.BUY, TradeStatus.PENDING, LocalDateTime.now().plusMinutes(1))).isEqualTo(1);
    assertThat(tradeRepository.countByTradeTypeAndStatusAndCreatedAtBefore(
      TradeType.BUY, TradeStatus.PENDING, LocalDateTime.now().minusMinutes(1))).isZero();
  }

  @Test
  @DisplayName("체결된 거래의 사용자 · 계좌 · 종목을 겹치지 않게 주고 취소만 있는 자리는 뺀다")
  void distinctPositions_CompletedOnly() {
    save(USER, 10, "AAPL", TradeType.BUY, "1", "100", LocalDate.of(2026, 10, 1), 9);
    save(USER, 10, "AAPL", TradeType.SELL, "1", "100", LocalDate.of(2026, 10, 2), 9);
    save(USER, 20, "AAPL", TradeType.BUY, "1", "100", LocalDate.of(2026, 10, 3), 9);
    save(OTHER_USER, 30, "TSLA", TradeType.BUY, "1", "100", LocalDate.of(2026, 10, 3), 9, TradeStatus.CANCELLED);

    assertThat(tradeRepository.findDistinctPositions(TradeStatus.COMPLETED)).containsExactlyInAnyOrder(
      new TradePosition(USER, 10L, "AAPL"), new TradePosition(USER, 20L, "AAPL"));
  }

  @Nested
  @DisplayName("매도 가능 수량 (매도일까지와 뒤 체결마다의 누적 보유 가운데 최솟값)")
  class SellableQuantity {

    @BeforeEach
    void seed() {
      save(USER, 10, "AAPL", TradeType.BUY, "10", "100", LocalDate.of(2026, 10, 1), 9);
      save(USER, 10, "AAPL", TradeType.BUY, "100", "1000", LocalDate.of(2026, 10, 2), 9, TradeStatus.CANCELLED);
      save(USER, 10, "AAPL", TradeType.SELL, "4", "40", LocalDate.of(2026, 10, 3), 9);
      save(USER, 10, "AAPL", TradeType.BUY, "5", "50", LocalDate.of(2026, 10, 5), 9);
      save(USER, 20, "AAPL", TradeType.BUY, "50", "500", LocalDate.of(2026, 10, 1), 9);
      save(USER, 10, "MSFT", TradeType.BUY, "7", "70", LocalDate.of(2026, 10, 1), 9);
    }

    private BigDecimal sellable(LocalDate on) {
      return tradeRepository.calculateSellableQuantity(USER, 10L, "AAPL", on);
    }

    @Test
    @DisplayName("체결된 같은 계좌 · 같은 종목만 세고, 그 날짜 이후 매수는 넣지 않는다")
    void countsCompletedSameAccountSymbolUpToDate() {
      assertThat(sellable(LocalDate.of(2026, 10, 4))).isEqualByComparingTo("6");
      assertThat(sellable(LocalDate.of(2026, 10, 10))).isEqualByComparingTo("11");
    }

    @Test
    @DisplayName("거래 전이거나 매도가 매수보다 많으면 0 이다")
    void zeroFloor() {
      assertThat(sellable(LocalDate.of(2026, 9, 30))).isEqualByComparingTo("0");

      save(USER, 10, "AAPL", TradeType.SELL, "20", "200", LocalDate.of(2026, 10, 6), 9);
      assertThat(sellable(LocalDate.of(2026, 10, 6))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("매도일 뒤 날짜의 누적 보유가 더 작으면 뒤 보유까지만 판다(1일 매수 10 · 3일 매도 10 · 4일 매수 10 이면 2일에 0)")
    void limitedByLaterHoldings() {
      save(USER, 30, "NVDA", TradeType.BUY, "10", "100", LocalDate.of(2026, 10, 1), 9);
      save(USER, 30, "NVDA", TradeType.SELL, "10", "100", LocalDate.of(2026, 10, 3), 9);
      save(USER, 30, "NVDA", TradeType.BUY, "10", "100", LocalDate.of(2026, 10, 4), 9);

      assertThat(tradeRepository.calculateSellableQuantity(USER, 30L, "NVDA", LocalDate.of(2026, 10, 2)))
        .isEqualByComparingTo("0");
      assertThat(tradeRepository.calculateSellableQuantity(USER, 30L, "NVDA", LocalDate.of(2026, 10, 4)))
        .isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("뒤 날짜 안에서도 체결 시각 순서로 보유가 음수가 되지 않는 만큼만 판다(5일 매도 5 뒤 매수 5 면 3일에 5)")
    void limitedWithinLaterDay() {
      save(USER, 40, "AMD", TradeType.BUY, "10", "100", LocalDate.of(2026, 10, 1), 9);
      save(USER, 40, "AMD", TradeType.SELL, "5", "50", LocalDate.of(2026, 10, 5), 9);
      save(USER, 40, "AMD", TradeType.BUY, "5", "50", LocalDate.of(2026, 10, 5), 10);

      assertThat(tradeRepository.calculateSellableQuantity(USER, 40L, "AMD", LocalDate.of(2026, 10, 3)))
        .isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("매도일 뒤 날짜들의 누적 보유 가운데 가장 작은 값까지 판다")
    void minimumOfLaterHoldings() {
      // 날짜순 누적 보유 10(1일) → 6(3일) → 11(5일)
      assertThat(sellable(LocalDate.of(2026, 10, 2))).isEqualByComparingTo("6");
      assertThat(sellable(LocalDate.of(2026, 10, 4))).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("매수 합과 매도 합을 쿼리 하나로 구한다")
    void singleQuery() {
      assertThat(statementsOf(() -> sellable(LocalDate.of(2026, 10, 10)))).isEqualTo(1);
    }
  }
}
