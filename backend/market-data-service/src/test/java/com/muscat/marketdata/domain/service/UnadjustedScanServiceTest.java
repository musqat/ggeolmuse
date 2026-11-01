package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.domain.repository.CandleRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("분할 종목 탐색")
class UnadjustedScanServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

  @Mock
  private CandleRepository candleRepository;

  private EmbeddedDatabase db;
  private UnadjustedScanService service;

  @BeforeEach
  void setUp() {
    db = AdminJobStates.database();
    service = new UnadjustedScanService(candleRepository, AdminJobStates.pod(db));
    ReflectionTestUtils.setField(service, "self", service);
  }

  @AfterEach
  void tearDown() {
    db.shutdown();
  }

  @Test
  @DisplayName("시작일 이후 분할 계수가 기록된 종목 목록을 그대로 담는다")
  void 분할_기록_위임() {
    given(candleRepository.findSymbolsWithSplits(FROM)).willReturn(List.of("AAPL", "NVDA"));

    service.scan(FROM);

    assertThat(service.getLast().getSymbols()).containsExactly("AAPL", "NVDA");
    assertThat(service.getLast().getFrom()).isEqualTo(FROM);
  }

  @Test
  @DisplayName("시작하면 탐색을 돌리고, 이미 돌고 있으면 시작하지 않는다")
  void startOnlyOnce() {
    given(candleRepository.findSymbolsWithSplits(LocalDate.of(2020, 1, 1))).willReturn(List.of("NVDA"));

    assertThat(service.start(LocalDate.of(2020, 1, 1))).isTrue();
    assertThat(service.getLast().getSymbols()).containsExactly("NVDA");
    assertThat(service.isRunning()).isFalse();

    AdminJobStates.pod(db).tryStart(UnadjustedScanService.JOB_NAME);
    assertThat(service.start(LocalDate.of(2020, 1, 1))).isFalse();
  }

  @Test
  @DisplayName("다른 파드가 돌리는 탐색은 진행 중으로 보이고 이 파드에서 시작하지 않는다")
  void otherPodRunning() {
    AdminJobStates.pod(db).tryStart(UnadjustedScanService.JOB_NAME);

    assertThat(service.isRunning()).isTrue();
    assertThat(service.start(FROM)).isFalse();
    verify(candleRepository, never()).findSymbolsWithSplits(any());
  }

  @Test
  @DisplayName("다른 파드가 끝낸 탐색 결과가 이 파드에서 보인다")
  void otherPodResultVisible() {
    given(candleRepository.findSymbolsWithSplits(FROM)).willReturn(List.of("AAPL"));
    UnadjustedScanService otherPod = new UnadjustedScanService(candleRepository, AdminJobStates.pod(db));

    otherPod.scan(FROM);

    assertThat(service.getLast().getSymbols()).containsExactly("AAPL");
    assertThat(service.getLast().getFinishedAt()).isNotNull();
  }

  @Test
  @DisplayName("조회가 실패하면 빈 결과에 오류를 담고 진행 표시를 내린다")
  void failureRecorded() {
    given(candleRepository.findSymbolsWithSplits(LocalDate.of(2020, 1, 1))).willThrow(new IllegalStateException("db down"));

    service.scan(LocalDate.of(2020, 1, 1));

    assertThat(service.getLast().getCount()).isZero();
    assertThat(service.getLast().getSymbols()).isEmpty();
    assertThat(service.getLast().getError()).isEqualTo("db down");
    assertThat(service.isRunning()).isFalse();
  }
}
