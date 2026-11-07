package com.muscat.user.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.muscat.user.UserApplication;
import com.muscat.user.domain.account.repository.IdempotencyRecordRepository;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("하루 지난 중복 요청 키 지우기")
class IdempotencyKeyCleanerTest {

  @Mock
  private IdempotencyRecordRepository idempotencyRecordRepository;

  @InjectMocks
  private IdempotencyKeyCleaner cleaner;

  @Test
  @DisplayName("지금부터 하루 전보다 먼저 만든 키를 지운다")
  void deleteExpired_CutoffIsOneDayAgo() {
    given(idempotencyRecordRepository.deleteByCreatedAtBefore(any())).willReturn(3L);
    LocalDateTime before = LocalDateTime.now().minusDays(1);

    cleaner.deleteExpired();

    ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
    InOrder order = inOrder(idempotencyRecordRepository);
    order.verify(idempotencyRecordRepository).deleteByCreatedAtBefore(cutoff.capture());
    order.verify(idempotencyRecordRepository).flush();
    assertThat(cutoff.getValue()).isBetween(before, LocalDateTime.now().minusDays(1));
  }

  @Test
  @DisplayName("스케줄이 켜져 있고 한 시간마다 한 트랜잭션으로 돈다")
  void scheduledHourly() throws NoSuchMethodException {
    Method method = IdempotencyKeyCleaner.class.getMethod("deleteExpired");

    assertThat(UserApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    assertThat(method.getAnnotation(Scheduled.class).fixedDelayString()).isEqualTo("PT1H");
    assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
  }
}
