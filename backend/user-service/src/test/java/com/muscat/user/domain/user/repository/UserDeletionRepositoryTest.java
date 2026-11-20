package com.muscat.user.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.UserPostgresJpaTest;
import com.muscat.user.domain.user.entity.UserDeletion;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@UserPostgresJpaTest
@DisplayName("탈퇴 기록 레포지토리")
class UserDeletionRepositoryTest {

  private static final LocalDateTime T = LocalDateTime.of(2026, 11, 21, 9, 0);

  @Autowired
  private UserDeletionRepository userDeletionRepository;

  @Autowired
  private TestEntityManager entityManager;

  private void save(String keycloakId, LocalDateTime publishedAt, LocalDateTime keycloakDeletedAt) {
    UserDeletion deletion = UserDeletion.of(keycloakId, T);
    if (publishedAt != null) {
      deletion.markEventPublished(publishedAt);
    }
    if (keycloakDeletedAt != null) {
      deletion.markKeycloakDeleted(keycloakDeletedAt);
    }
    userDeletionRepository.saveAndFlush(deletion);
  }

  @Test
  @DisplayName("이벤트 발행이나 Keycloak 삭제가 남은 기록만 찾는다")
  void findPending() {
    save("kc-none", null, null);
    save("kc-event", T, null);
    save("kc-keycloak", null, T);
    save("kc-done", T, T);
    entityManager.clear();

    assertThat(userDeletionRepository.findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull())
      .extracting(UserDeletion::getKeycloakId)
      .containsExactlyInAnyOrder("kc-none", "kc-event", "kc-keycloak");
  }

  @Test
  @DisplayName("둘 다 끝나고 기준 시각 전에 Keycloak 삭제가 끝난 기록만 지운다")
  void deleteDoneBefore() {
    save("kc-old", T.minusDays(2), T.minusDays(2));
    save("kc-recent", T, T);
    save("kc-pending", null, T.minusDays(2));
    entityManager.clear();

    long deleted = userDeletionRepository.deleteByEventPublishedAtIsNotNullAndKeycloakDeletedAtBefore(T.minusDays(1));

    assertThat(deleted).isEqualTo(1);
    assertThat(userDeletionRepository.existsById("kc-old")).isFalse();
    assertThat(userDeletionRepository.existsById("kc-recent")).isTrue();
    assertThat(userDeletionRepository.existsById("kc-pending")).isTrue();
  }
}
