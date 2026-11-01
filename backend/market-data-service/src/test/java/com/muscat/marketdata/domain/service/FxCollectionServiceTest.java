package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.datasource.common.FxDataCollector;
import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.FxCollectionService.Mode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 환율 수집")
class FxCollectionServiceTest {

  private static final LocalDate START = LocalDate.of(2014, 1, 1);

  @Mock
  private FxDataCollector collector;

  @Mock
  private FxRateRepository fxRateRepository;

  @Mock
  private AdminJobRunRepository adminJobRunRepository;

  private MarketDataProperties properties;
  private EmbeddedDatabase db;
  private FxCollectionService service;

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  // 2024-09-02(월) 부터 평일 n 개
  private static List<LocalDate> weekdays(int n) {
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate d = LocalDate.of(2024, 9, 2); days.size() < n; d = d.plusDays(1)) {
      if (d.getDayOfWeek().getValue() <= 5) {
        days.add(d);
      }
    }
    return days;
  }

  private static Optional<FxRate> rate(LocalDate date) {
    return Optional.of(FxRate.builder().date(date).rate(new BigDecimal("1350")).currencyPair("USD/KRW").build());
  }

  @BeforeEach
  void setUp() {
    properties = new MarketDataProperties();
    properties.getFxIngest().setStartDate(START);
    db = AdminJobStates.database();
    service = new FxCollectionService(collector, fxRateRepository, adminJobRunRepository, properties,
        AdminJobStates.pod(db));
  }

  @AfterEach
  void tearDown() {
    db.shutdown();
  }

  @Test
  @DisplayName("빠진 날 채우기는 테이블에 없는 평일만 최근 날부터 받아 저장하고 결과를 기록 테이블에 남긴다")
  void missingOnly() {
    List<LocalDate> missing = weekdays(2);
    given(collector.missingWeekdays(START, today())).willReturn(missing);
    given(collector.collectSingleDate(any(), eq(true))).willAnswer(i -> rate(i.getArgument(0)));

    service.collect(Mode.MISSING);

    ArgumentCaptor<FxRate> saved = ArgumentCaptor.forClass(FxRate.class);
    verify(fxRateRepository, times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).extracting(FxRate::getDate).containsExactlyElementsOf(missing.reversed());
    FxCollectionService.Progress last = service.lastProgress();
    assertThat(last.getMode()).isEqualTo(Mode.MISSING);
    assertThat(last.getTotal()).isEqualTo(2);
    assertThat(last.getFilled()).isEqualTo(2);
    assertThat(last.getFailed()).isZero();
    assertThat(last.getFinishedAt()).isNotNull();
    ArgumentCaptor<AdminJobRun> run = ArgumentCaptor.forClass(AdminJobRun.class);
    verify(adminJobRunRepository).save(run.capture());
    assertThat(run.getValue().getName()).isEqualTo("fx-collect");
    assertThat(run.getValue().getPublished()).isEqualTo(2);
  }

  @Test
  @DisplayName("전체 받기는 저장된 날과 상관없이 시작일 ~ 오늘의 모든 평일을 받는다")
  void allWeekdays() {
    LocalDate start = today().minusDays(13);
    properties.getFxIngest().setStartDate(start);
    List<LocalDate> expected = FxDataCollector.weekdays(start, today());
    given(collector.collectSingleDate(any(), eq(true))).willAnswer(i -> rate(i.getArgument(0)));

    service.collect(Mode.ALL);

    verify(fxRateRepository, times(expected.size())).save(any());
    verify(collector, never()).missingWeekdays(any(), any());
    assertThat(service.lastProgress().getFilled()).isEqualTo(expected.size());
  }

  @Test
  @DisplayName("원천이 연속 10 영업일 값을 주지 않으면 멈추고 이유와 마지막 처리일을 남긴다(최근 날부터 열흘)")
  void stopsAfterConsecutiveFailures() {
    List<LocalDate> missing = weekdays(15);
    given(collector.missingWeekdays(START, today())).willReturn(missing);
    given(collector.collectSingleDate(any(), anyBoolean())).willThrow(new IllegalStateException("환율 폴백 기간 초과"));

    service.collect(Mode.MISSING);

    FxCollectionService.Progress last = service.lastProgress();
    assertThat(last.getProcessed()).isEqualTo(10);
    assertThat(last.getFailed()).isEqualTo(10);
    assertThat(last.getLastDate()).isEqualTo(missing.get(5));
    assertThat(last.getStoppedReason()).contains("연속 10 영업일");
    verify(fxRateRepository, never()).save(any());
  }

  @Test
  @DisplayName("중간에 하루라도 받으면 연속 실패를 처음부터 다시 센다")
  void successResetsFailures() {
    List<LocalDate> missing = weekdays(20);
    given(collector.missingWeekdays(START, today())).willReturn(missing);
    given(collector.collectSingleDate(any(), eq(true))).willReturn(Optional.empty());
    given(collector.collectSingleDate(eq(missing.get(9)), eq(true))).willReturn(rate(missing.get(9)));
    given(collector.collectSingleDate(eq(missing.get(19)), eq(true))).willReturn(rate(missing.get(19)));

    service.collect(Mode.MISSING);

    FxCollectionService.Progress last = service.lastProgress();
    assertThat(last.getProcessed()).isEqualTo(20);
    assertThat(last.getFilled()).isEqualTo(2);
    assertThat(last.getFailed()).isEqualTo(18);
    assertThat(last.getStoppedReason()).isNull();
  }

  @Test
  @DisplayName("시작하면 수집을 돌리고, 이미 돌고 있으면 시작하지 않는다")
  void startOnlyOnce() {
    ReflectionTestUtils.setField(service, "self", service);
    given(collector.missingWeekdays(START, today())).willReturn(List.of());

    assertThat(service.start(Mode.MISSING)).isTrue();
    assertThat(service.isRunning()).isFalse();

    AdminJobStates.pod(db).tryStart(FxCollectionService.JOB_NAME);
    assertThat(service.start(Mode.ALL)).isFalse();
  }

  @Test
  @DisplayName("다른 파드가 돌리는 수집은 진행 중으로 보이고 그 진행이 상태에 나온다")
  void otherPodProgressVisible() {
    given(collector.missingWeekdays(START, today())).willReturn(List.of());
    AdminJobState otherPod = AdminJobStates.pod(db);
    otherPod.tryStart(FxCollectionService.JOB_NAME);
    otherPod.save(FxCollectionService.JOB_NAME,
        FxCollectionService.Progress.builder().mode(Mode.ALL).total(30).processed(12).build());

    FxCollectionService.Status status = service.status();

    assertThat(status.running()).isTrue();
    assertThat(status.progress().getProcessed()).isEqualTo(12);
    assertThat(status.progress().getMode()).isEqualTo(Mode.ALL);
    assertThat(service.start(Mode.MISSING)).isFalse();
  }

  @Test
  @DisplayName("상태는 빈 날 수와 최근 빈 날 열 개를 최근 날부터 준다")
  void statusShowsRecentMissing() {
    List<LocalDate> missing = weekdays(12);
    given(collector.missingWeekdays(START, today())).willReturn(missing);

    FxCollectionService.Status status = service.status();

    assertThat(status.missingCount()).isEqualTo(12);
    assertThat(status.missingDates()).hasSize(10);
    assertThat(status.missingDates().get(0)).isEqualTo(missing.get(11));
    assertThat(status.progress()).isNull();
  }

  @Test
  @DisplayName("다시 띄워 진행 기록이 없으면 기록 테이블의 마지막 실행 시각과 채운 수를 준다")
  void statusFallsBackToJobRun() {
    given(collector.missingWeekdays(START, today())).willReturn(List.of());
    LocalDateTime at = LocalDateTime.of(2026, 10, 25, 3, 0);
    given(adminJobRunRepository.findById("fx-collect")).willReturn(Optional.of(new AdminJobRun("fx-collect", at, 7)));

    FxCollectionService.Progress last = service.status().progress();

    assertThat(last.getFinishedAt()).isEqualTo(at);
    assertThat(last.getFilled()).isEqualTo(7);
    assertThat(last.getMode()).isNull();
  }

  @Test
  @DisplayName("수집이 예외로 끝나면 이유를 남기고 진행 표시를 내린다")
  void failureRecorded() {
    given(collector.missingWeekdays(START, today())).willThrow(new IllegalStateException("db down"));

    service.collect(Mode.MISSING);

    assertThat(service.isRunning()).isFalse();
    assertThat(service.lastProgress().getStoppedReason()).isEqualTo("db down");
    assertThat(service.lastProgress().getFinishedAt()).isNotNull();
  }
}
