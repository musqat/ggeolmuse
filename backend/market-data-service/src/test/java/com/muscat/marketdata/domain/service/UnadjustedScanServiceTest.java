package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.muscat.marketdata.domain.repository.CandleRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("분할 종목 탐색")
class UnadjustedScanServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

  @Mock
  private CandleRepository candleRepository;

  private UnadjustedScanService service;

  @BeforeEach
  void setUp() {
    service = new UnadjustedScanService(candleRepository);
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
    org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
    given(candleRepository.findSymbolsWithSplits(LocalDate.of(2020, 1, 1))).willReturn(List.of("NVDA"));

    assertThat(service.start(LocalDate.of(2020, 1, 1))).isTrue();
    assertThat(service.getLast().getSymbols()).containsExactly("NVDA");
    assertThat(service.isRunning()).isFalse();

    ((java.util.concurrent.atomic.AtomicBoolean) org.springframework.test.util.ReflectionTestUtils.getField(service, "running")).set(true);
    assertThat(service.start(LocalDate.of(2020, 1, 1))).isFalse();
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
