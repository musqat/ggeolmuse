package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@DisplayName("체결 레포지토리 - 이벤트 발행 기록")
class TradeRepositoryTest {

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private TestEntityManager entityManager;

  private Trade trade(LocalDateTime eventPublishedAt) {
    return tradeRepository.save(Trade.builder()
      .userId("user-1")
      .accountId(20L)
      .symbol("AAPL")
      .tradeType(TradeType.BUY)
      .quantity(new BigDecimal("2"))
      .price(new BigDecimal("333.22"))
      .totalAmount(new BigDecimal("668.11"))
      .fee(new BigDecimal("1.67"))
      .tradeDate(LocalDate.of(2026, 10, 7))
      .executedAt(LocalDateTime.of(2026, 10, 7, 9, 30))
      .eventPublishedAt(eventPublishedAt)
      .build());
  }

  @Test
  @DisplayName("못 보낸 체결만 id 순으로 찾고 센다")
  void findUnpublished_ReturnsOnlyUnpublishedInIdOrder() {
    Trade first = trade(null);
    trade(LocalDateTime.of(2026, 10, 7, 9, 31));
    Trade third = trade(null);
    LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);

    List<Trade> found =
      tradeRepository.findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(cutoff);

    assertThat(found).extracting(Trade::getId).containsExactly(first.getId(), third.getId());
    assertThat(tradeRepository.countByEventPublishedAtIsNullAndCreatedAtBefore(cutoff)).isEqualTo(2);
  }

  @Test
  @DisplayName("기준 시각 뒤에 만든 체결은 찾지 않는다")
  void findUnpublished_SkipsTradesCreatedAfterCutoff() {
    trade(null);

    assertThat(tradeRepository.findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(
      LocalDateTime.now().minusMinutes(1))).isEmpty();
  }

  @Test
  @DisplayName("보낸 시각을 기록한다")
  void markEventPublished_SetsTimestamp() {
    Trade saved = trade(null);
    LocalDateTime publishedAt = LocalDateTime.of(2026, 10, 8, 9, 0);

    int updated = tradeRepository.markEventPublished(saved.getId(), publishedAt);
    entityManager.clear();

    assertThat(updated).isEqualTo(1);
    assertThat(tradeRepository.findById(saved.getId()).orElseThrow().getEventPublishedAt())
      .isEqualTo(publishedAt);
  }
}
