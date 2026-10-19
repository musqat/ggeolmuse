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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@DisplayName("체결 레포지토리 - QueryDSL 조회")
class TradeRepositoryQueryTest {

  private static final String USER = "user-1";
  private static final String OTHER_USER = "user-2";

  @Autowired
  private TradeRepository tradeRepository;

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
  @DisplayName("거래한 종목을 사용자별로 중복 없이 준다")
  void distinctSymbols_PerUser() {
    save(USER, 10, "AAPL", TradeType.BUY, "1", "100", LocalDate.of(2026, 10, 1), 9);
    save(USER, 10, "AAPL", TradeType.SELL, "1", "100", LocalDate.of(2026, 10, 2), 9);
    save(USER, 20, "MSFT", TradeType.BUY, "1", "100", LocalDate.of(2026, 10, 3), 9);
    save(OTHER_USER, 30, "TSLA", TradeType.BUY, "1", "100", LocalDate.of(2026, 10, 3), 9);

    assertThat(tradeRepository.findDistinctSymbolsByUserId(USER)).containsExactlyInAnyOrder("AAPL", "MSFT");
    assertThat(tradeRepository.findDistinctSymbolsByUserId("nobody")).isEmpty();
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
  @DisplayName("조건 조립 조회")
  class ComplexFilters {

    private final LocalDate d1 = LocalDate.of(2026, 10, 1);
    private final LocalDate d3 = LocalDate.of(2026, 10, 3);
    private final LocalDate d5 = LocalDate.of(2026, 10, 5);
    private final LocalDate d7 = LocalDate.of(2026, 10, 7);
    private final LocalDate d9 = LocalDate.of(2026, 10, 9);

    @BeforeEach
    void seed() {
      save(USER, 10, "AAPL", TradeType.BUY, "1", "100", d1, 9);
      save(USER, 10, "AAPL", TradeType.SELL, "1", "50", d3, 10);
      save(USER, 20, "MSFT", TradeType.BUY, "1", "300", d5, 11);
      save(USER, 20, "MSFT", TradeType.BUY, "1", "500", d7, 12);
      save(USER, 10, "TSLA", TradeType.BUY, "1", "1000", d9, 13);
      save(OTHER_USER, 10, "AAPL", TradeType.BUY, "1", "100", d9, 14);
    }

    private Page<Trade> find(Long accountId, String symbol, TradeType type, LocalDate start, LocalDate end,
      String min, String max, int size) {
      return tradeRepository.findTradesWithComplexFilters(USER, accountId, symbol, type, start, end,
        min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), PageRequest.of(0, size));
    }

    private List<String> totals(Page<Trade> page) {
      return page.getContent().stream().map(t -> t.getTotalAmount().stripTrailingZeros().toPlainString()).toList();
    }

    @Test
    @DisplayName("조건이 전부 비면 내 거래 전부를 체결 시각 내림차순으로 주고, 페이지 크기만큼 자르되 전체 건수는 그대로다")
    void noFilters_AllMineNewestFirst_Paged() {
      Page<Trade> all = find(null, null, null, null, null, null, null, 10);
      Page<Trade> page = find(null, null, null, null, null, null, null, 2);

      assertThat(totals(all)).containsExactly("1000", "500", "300", "50", "100");
      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getTotalElements()).isEqualTo(5);
      assertThat(totals(page)).containsExactly("1000", "500");
    }

    @Test
    @DisplayName("계좌 · 종목 · 매매 구분은 각각 따로 거른다")
    void singleFilters() {
      assertThat(find(10L, null, null, null, null, null, null, 10).getTotalElements()).isEqualTo(3);
      assertThat(find(null, "MSFT", null, null, null, null, null, 10).getTotalElements()).isEqualTo(2);
      assertThat(find(null, null, TradeType.SELL, null, null, null, null, 10).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("거래일은 시작만 · 끝만 · 둘 다 로 거르고 양 끝 날짜를 포함한다")
    void dateRanges() {
      assertThat(totals(find(null, null, null, d5, null, null, null, 10))).containsExactly("1000", "500", "300");
      assertThat(totals(find(null, null, null, null, d3, null, null, 10))).containsExactly("50", "100");
      assertThat(totals(find(null, null, null, d3, d7, null, null, 10))).containsExactly("500", "300", "50");
    }

    @Test
    @DisplayName("거래 금액은 최소만 · 최대만 · 둘 다 로 거르고 양 끝 값을 포함한다")
    void amountRanges() {
      assertThat(totals(find(null, null, null, null, null, "300", null, 10))).containsExactly("1000", "500", "300");
      assertThat(totals(find(null, null, null, null, null, null, "100", 10))).containsExactly("50", "100");
      assertThat(totals(find(null, null, null, null, null, "50", "300", 10))).containsExactly("300", "50", "100");
    }

    @Test
    @DisplayName("조건을 전부 주면 모두 만족하는 거래만 남는다")
    void allFilters() {
      Page<Trade> page = find(20L, "MSFT", TradeType.BUY, d5, d7, "300", "500", 10);

      assertThat(totals(page)).containsExactly("500", "300");
      assertThat(find(20L, "MSFT", TradeType.SELL, d5, d7, "300", "500", 10).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("페이지를 나누지 않으면(unpaged) 기간 안 내 거래를 전부 준다")
    void unpaged_ReturnsAllInRange() {
      Page<Trade> page = tradeRepository.findTradesWithComplexFilters(USER, null, null, null, d1, d7,
        null, null, Pageable.unpaged());

      assertThat(totals(page)).containsExactly("500", "300", "50", "100");
      assertThat(page.getTotalElements()).isEqualTo(4);
    }
  }

  @Nested
  @DisplayName("매도 가능 수량 (해당 날짜까지의 매수 합 − 매도 합)")
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
  }
}
