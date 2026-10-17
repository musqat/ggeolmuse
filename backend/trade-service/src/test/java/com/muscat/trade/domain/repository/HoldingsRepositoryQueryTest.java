package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.domain.entity.Holdings;
import java.math.BigDecimal;
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

  private void holding(String userId, long accountId, String symbol, String quantity, String invested) {
    holdingsRepository.save(Holdings.builder()
      .userId(userId).accountId(accountId).symbol(symbol)
      .totalQuantity(new BigDecimal(quantity)).avgPurchasePrice(BigDecimal.TEN)
      .totalInvestedAmount(new BigDecimal(invested))
      .build());
  }

  @BeforeEach
  void seed() {
    holding(USER, 10, "AAPL", "10", "1000");
    holding(USER, 10, "MSFT", "3", "2500");
    holding(USER, 20, "NVDA", "5", "0");
    holding(OTHER_USER, 30, "AAPL", "1", "99999");
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
  @DisplayName("사용자 · 계좌 · 종목으로 보유 하나를 찾고, 다른 계좌면 비어 있다")
  void byUserAccountSymbol() {
    assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(USER, 10L, "AAPL"))
      .hasValueSatisfying(h -> assertThat(h.getTotalQuantity()).isEqualByComparingTo("10"));
    assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(USER, 20L, "AAPL")).isEmpty();
  }
}
