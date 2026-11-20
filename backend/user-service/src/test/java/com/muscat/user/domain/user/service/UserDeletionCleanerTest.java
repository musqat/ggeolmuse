package com.muscat.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.user.domain.user.entity.UserDeletion;
import com.muscat.user.domain.user.repository.UserDeletionRepository;
import com.muscat.user.infra.kafka.UserEventProducer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("탈퇴 정리")
class UserDeletionCleanerTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 9, 0);

  private final UserDeletionRepository repository = mock(UserDeletionRepository.class);
  private final UserEventProducer producer = mock(UserEventProducer.class);
  private final KeycloakService keycloakService = mock(KeycloakService.class);
  private final UserDeletionCleaner cleaner = new UserDeletionCleaner(repository, producer, keycloakService,
    Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));

  @Test
  @DisplayName("탈퇴 기록은 Keycloak ID 와 탈퇴 시각으로 남긴다")
  void record_SavesKeycloakIdAndTime() {
    cleaner.record("kc-1");

    ArgumentCaptor<UserDeletion> saved = ArgumentCaptor.forClass(UserDeletion.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getKeycloakId()).isEqualTo("kc-1");
    assertThat(saved.getValue().getDeletedAt()).isEqualTo(NOW);
    assertThat(saved.getValue().isDone()).isFalse();
  }

  @Test
  @DisplayName("바로 정리하면 사용자 삭제 이벤트를 보내고 Keycloak 계정을 지운 뒤 둘 다 끝났다고 남긴다")
  void cleanUp_PublishesAndDeletesKeycloak() {
    UserDeletion deletion = UserDeletion.of("kc-1", NOW);
    given(repository.findById("kc-1")).willReturn(Optional.of(deletion));

    cleaner.cleanUp("kc-1");

    verify(producer).publishUserDeleted("kc-1");
    verify(keycloakService).deleteUser("kc-1");
    assertThat(deletion.getEventPublishedAt()).isEqualTo(NOW);
    assertThat(deletion.getKeycloakDeletedAt()).isEqualTo(NOW);
    verify(repository).save(deletion);
  }

  @Test
  @DisplayName("Kafka · Keycloak 이 실패하면 끝났다고 남기지 않고 예외를 탈퇴 응답으로 넘기지 않는다")
  void cleanUp_Failures_LeftPending() {
    UserDeletion deletion = UserDeletion.of("kc-1", NOW);
    given(repository.findById("kc-1")).willReturn(Optional.of(deletion));
    willThrow(new IllegalStateException("broker down")).given(producer).publishUserDeleted("kc-1");
    willThrow(new IllegalStateException("keycloak down")).given(keycloakService).deleteUser("kc-1");

    assertThatCode(() -> cleaner.cleanUp("kc-1")).doesNotThrowAnyException();

    verify(keycloakService).deleteUser("kc-1");
    assertThat(deletion.getEventPublishedAt()).isNull();
    assertThat(deletion.getKeycloakDeletedAt()).isNull();
  }

  @Test
  @DisplayName("기록을 읽다가 실패해도 탈퇴 응답으로 예외를 넘기지 않는다")
  void cleanUp_RepositoryFailure_Swallowed() {
    given(repository.findById("kc-1")).willThrow(new IllegalStateException("db down"));

    assertThatCode(() -> cleaner.cleanUp("kc-1")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("다시 정리는 남은 단계만 하고, 끝난 지 하루 지난 기록을 지운다")
  void retryPending_OnlyRemainingStepsAndPurges() {
    UserDeletion published = UserDeletion.of("kc-1", NOW.minusHours(1));
    published.markEventPublished(NOW.minusHours(1));
    given(repository.findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull()).willReturn(List.of(published));

    cleaner.retryPending();

    verify(producer, never()).publishUserDeleted(any());
    verify(keycloakService).deleteUser("kc-1");
    assertThat(published.isDone()).isTrue();
    verify(repository).deleteByEventPublishedAtIsNotNullAndKeycloakDeletedAtBefore(NOW.minusDays(1));
  }
}
