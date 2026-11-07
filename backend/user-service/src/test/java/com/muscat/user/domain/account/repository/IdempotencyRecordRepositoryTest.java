package com.muscat.user.domain.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.UserJpaTestConfig;
import com.muscat.user.domain.account.entity.IdempotencyRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = UserJpaTestConfig.class)
@DisplayName("중복 요청 키 기록 레포지토리")
class IdempotencyRecordRepositoryTest {

  private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 6, 12, 0);

  @Autowired
  private IdempotencyRecordRepository idempotencyRecordRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("기준 시각보다 먼저 만든 키만 지우고 지운 수를 돌려준다")
  void deleteByCreatedAtBefore_OnlyOlder() {
    idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.of(1L, "old", "f", CUTOFF.minusMinutes(1)));
    idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.of(1L, "edge", "f", CUTOFF));
    idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.of(2L, "new", "f", CUTOFF.plusHours(1)));
    entityManager.clear();

    long deleted = idempotencyRecordRepository.deleteByCreatedAtBefore(CUTOFF);
    entityManager.flush();
    entityManager.clear();

    assertThat(deleted).isEqualTo(1);
    assertThat(idempotencyRecordRepository.findAll())
      .extracting(IdempotencyRecord::getIdempotencyKey).containsExactlyInAnyOrder("edge", "new");
  }
}
