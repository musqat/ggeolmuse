package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.service.DividendAmounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@DisplayName("배당 몫 레포지토리")
class DividendPayoutRepositoryQueryTest {

  private static final String USER = "user-1";

  @Autowired
  private DividendPayoutRepository dividendPayoutRepository;

  private void payout(String userId, long accountId, LocalDate exDate, String net) {
    dividendPayoutRepository.save(DividendPayout.of(userId, accountId, "AAPL", exDate,
      new DividendAmounts(new BigDecimal("0.24"), new BigDecimal("10"), new BigDecimal("2.40"),
        new BigDecimal("0.37"), new BigDecimal(net)),
      LocalDateTime.of(2026, 10, 18, 9, 0)));
  }

  @BeforeEach
  void seed() {
    payout(USER, 20L, LocalDate.of(2024, 2, 9), "2.03");
    payout(USER, 20L, LocalDate.of(2025, 5, 12), "0.00");
    payout(USER, 21L, LocalDate.of(2026, 8, 11), "2.20");
    payout("user-2", 30L, LocalDate.of(2026, 8, 11), "9.99");
  }

  @Test
  @DisplayName("내역용 조회는 내 몫 중 세후가 0 보다 큰 것만 배당락일 최신순으로 준다")
  void findPositiveByUser_LatestFirst() {
    assertThat(dividendPayoutRepository.findByUserIdAndNetAmountGreaterThanOrderByExDateDesc(USER, BigDecimal.ZERO))
      .extracting(DividendPayout::getAccountId, DividendPayout::getExDate)
      .containsExactly(tuple(21L, LocalDate.of(2026, 8, 11)), tuple(20L, LocalDate.of(2024, 2, 9)));
  }

  @Test
  @DisplayName("계좌를 지우면 그 계좌의 몫만 사라진다")
  void deleteByAccountId_OnlyThatAccount() {
    dividendPayoutRepository.deleteByAccountId(20L);

    assertThat(dividendPayoutRepository.findAll()).extracting(DividendPayout::getAccountId)
      .containsExactlyInAnyOrder(21L, 30L);
  }

  @Test
  @DisplayName("몫이 있는 사용자 · 계좌 · 종목을 겹치지 않게 준다")
  void distinctPositions() {
    assertThat(dividendPayoutRepository.findDistinctPositions()).containsExactlyInAnyOrder(
      new TradePosition(USER, 20L, "AAPL"), new TradePosition(USER, 21L, "AAPL"), new TradePosition("user-2", 30L, "AAPL"));
  }
}
