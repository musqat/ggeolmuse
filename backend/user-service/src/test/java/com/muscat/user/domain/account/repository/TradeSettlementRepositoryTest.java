package com.muscat.user.domain.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.jpatest.UserJpaTestConfig;
import com.muscat.user.domain.account.entity.TradeSettlement;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = UserJpaTestConfig.class)
@DisplayName("체결 정산 기록 레포지토리")
class TradeSettlementRepositoryTest {

  @Autowired
  private TradeSettlementRepository tradeSettlementRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("처리한 체결은 ID 로 찾는다")
  void existsById_AfterSave() {
    tradeSettlementRepository.saveAndFlush(
      TradeSettlement.applied(8L, null, LocalDateTime.of(2026, 10, 8, 9, 0)));
    entityManager.clear();

    assertThat(tradeSettlementRepository.existsById(8L)).isTrue();
    assertThat(tradeSettlementRepository.existsById(9L)).isFalse();
  }

  @Test
  @DisplayName("같은 체결 ID 를 다시 넣으면 기본 키 충돌로 막힌다")
  void saveTwice_SameTradeId_Fails() {
    tradeSettlementRepository.saveAndFlush(
      TradeSettlement.applied(7L, 20L, LocalDateTime.of(2026, 10, 8, 9, 0)));
    entityManager.clear();

    assertThatThrownBy(() -> tradeSettlementRepository.saveAndFlush(
      TradeSettlement.applied(7L, 20L, LocalDateTime.of(2026, 10, 8, 9, 1))))
      .isInstanceOf(DataIntegrityViolationException.class);
  }
}
