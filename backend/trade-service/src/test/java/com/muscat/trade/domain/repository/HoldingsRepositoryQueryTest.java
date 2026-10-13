package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@DisplayName("보유 레포지토리 - QueryDSL 조회")
class HoldingsRepositoryQueryTest {

  private static final String USER = "user-1";
  private static final String OTHER_USER = "user-2";

  @Autowired
  private HoldingsRepository holdingsRepository;

  @Autowired
  private TradeRepository tradeRepository;

  private void holding(String userId, long accountId, String symbol, String quantity, String invested) {
    holdingsRepository.save(Holdings.builder()
      .userId(userId).accountId(accountId).symbol(symbol)
      .totalQuantity(new BigDecimal(quantity)).avgPurchasePrice(BigDecimal.TEN)
      .totalInvestedAmount(new BigDecimal(invested))
      .build());
  }

  private void trade(String userId, String symbol) {
    tradeRepository.save(Trade.builder()
      .userId(userId).accountId(10L).symbol(symbol).tradeType(TradeType.BUY)
      .quantity(BigDecimal.ONE).price(BigDecimal.TEN).totalAmount(BigDecimal.TEN).fee(BigDecimal.ZERO)
      .tradeDate(LocalDate.of(2026, 10, 1)).executedAt(LocalDate.of(2026, 10, 1).atTime(9, 0))
      .build());
  }

  private static List<String> symbols(List<Holdings> holdings) {
    return holdings.stream().map(Holdings::getSymbol).toList();
  }

  @BeforeEach
  void seed() {
    holding(USER, 10, "AAPL", "10", "1000");
    holding(USER, 10, "MSFT", "3", "2500");
    holding(USER, 20, "NVDA", "5", "0");
    holding(OTHER_USER, 30, "AAPL", "1", "99999");
    trade(USER, "AAPL");
    trade(USER, "AAPL");
    trade(USER, "MSFT");
    trade(OTHER_USER, "NVDA");
  }

  @Test
  @DisplayName("포트폴리오 요약은 내 보유의 투자금 합과 건수다. 보유가 없으면 0 · 0 이다")
  void portfolioSummary() {
    PortfolioSummaryProjection mine = holdingsRepository.calculatePortfolioSummary(USER);
    PortfolioSummaryProjection none = holdingsRepository.calculatePortfolioSummary("nobody");

    assertThat(mine.totalInvestedAmount()).isEqualByComparingTo("3500");
    assertThat(mine.holdingCount()).isEqualTo(3);
    assertThat(none.totalInvestedAmount()).isEqualByComparingTo("0");
    assertThat(none.holdingCount()).isZero();
  }

  @Test
  @DisplayName("거래 이력이 있는 보유만, 같은 사용자의 거래로만 센다. 종목 · 최소 수량으로 더 거른다")
  void holdingsWithTradeHistory() {
    assertThat(symbols(holdingsRepository.findHoldingsWithTradeHistory(USER, null, null)))
      .containsExactlyInAnyOrder("AAPL", "MSFT");
    assertThat(symbols(holdingsRepository.findHoldingsWithTradeHistory(USER, "AAPL", null))).containsExactly("AAPL");
    assertThat(symbols(holdingsRepository.findHoldingsWithTradeHistory(USER, null, 5))).containsExactly("AAPL");
    assertThat(holdingsRepository.findHoldingsWithTradeHistory(USER, "NVDA", null)).isEmpty();
  }

  @Test
  @DisplayName("투자금 상위 N 은 투자금 0 을 빼고 내림차순으로 자른다")
  void topHoldingsByInvestment() {
    assertThat(symbols(holdingsRepository.findTopHoldingsByInvestment(USER, 5))).containsExactly("MSFT", "AAPL");
    assertThat(symbols(holdingsRepository.findTopHoldingsByInvestment(USER, 1))).containsExactly("MSFT");
  }

  @Test
  @DisplayName("최소 투자금 이상인 보유를 투자금 내림차순으로 준다(경계 포함)")
  void holdingsByMinInvestment() {
    assertThat(symbols(holdingsRepository.findHoldingsByMinInvestment(USER, new BigDecimal("1000"))))
      .containsExactly("MSFT", "AAPL");
    assertThat(symbols(holdingsRepository.findHoldingsByMinInvestment(USER, new BigDecimal("1001"))))
      .containsExactly("MSFT");
  }

  @Test
  @DisplayName("사용자 · 계좌 · 종목으로 보유 하나를 찾고, 다른 계좌면 비어 있다")
  void byUserAccountSymbol() {
    assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(USER, 10L, "AAPL"))
      .hasValueSatisfying(h -> assertThat(h.getTotalQuantity()).isEqualByComparingTo("10"));
    assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(USER, 20L, "AAPL")).isEmpty();
  }
}
