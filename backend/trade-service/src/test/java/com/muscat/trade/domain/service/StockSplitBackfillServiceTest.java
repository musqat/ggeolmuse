package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.StockSplitBackfillService.Result;
import com.muscat.trade.domain.service.StockSplitBackfillService.Status;
import com.muscat.trade.domain.service.StockSplitService.Outcome;
import com.muscat.trade.domain.service.StockSplitService.Split;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.dto.SplitDto;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("지난 분할 백필")
class StockSplitBackfillServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 12, 0);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private StockSplitService stockSplitService;

  private final List<Runnable> queued = new ArrayList<>();
  private StockSplitBackfillService service;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    service = new StockSplitBackfillService(tradeRepository, marketServiceClientWrapper, stockSplitService,
      queued::add, Clock.fixed(Instant.parse("2026-11-13T03:00:00Z"), ZoneId.of("Asia/Seoul")), registry);
    ReflectionTestUtils.setField(service, "self", service);
  }

  private static Map<String, LocalDate> firstTradeDates(Object... pairs) {
    Map<String, LocalDate> dates = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      dates.put((String) pairs[i], (LocalDate) pairs[i + 1]);
    }
    return dates;
  }

  @Test
  @DisplayName("종목마다 첫 거래일 다음 날부터 난 분할을 받아 반영하고 맞춘 체결 수를 더함")
  void appliesSplitsAfterFirstTrade() {
    given(tradeRepository.findFirstTradeDateBySymbol()).willReturn(firstTradeDates(
      "NVDA", LocalDate.of(2024, 5, 1), "AAPL", LocalDate.of(2025, 1, 2), "TSLA", LocalDate.of(2022, 8, 1)));
    given(marketServiceClientWrapper.getSplits("NVDA", "2024-05-02")).willReturn(List.of(
      new SplitDto("2024-06-10", new BigDecimal("10.00000000"))));
    given(marketServiceClientWrapper.getSplits("AAPL", "2025-01-03")).willReturn(List.of());
    given(marketServiceClientWrapper.getSplits("TSLA", "2022-08-02")).willReturn(List.of(
      new SplitDto("2022-08-25", new BigDecimal("3"))));
    given(stockSplitService.apply("NVDA", List.of(new Split(LocalDate.of(2024, 6, 10), new BigDecimal("10.00000000")))))
      .willReturn(new Outcome(4, 0));
    given(stockSplitService.apply("TSLA", List.of(new Split(LocalDate.of(2022, 8, 25), new BigDecimal("3")))))
      .willReturn(new Outcome(0, 0));

    assertThat(service.applyAll()).isEqualTo(new Result(4, 0, 0, NOW));

    verify(stockSplitService, never()).apply(eq("AAPL"), anyList());
  }

  @Test
  @DisplayName("한 종목이 실패해도 나머지는 반영하고 실패한 종목 수를 셈")
  void oneSymbolFails_CountsFailure() {
    given(tradeRepository.findFirstTradeDateBySymbol()).willReturn(firstTradeDates(
      "NVDA", LocalDate.of(2024, 5, 1), "TSLA", LocalDate.of(2022, 8, 1)));
    given(marketServiceClientWrapper.getSplits("NVDA", "2024-05-02"))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));
    given(marketServiceClientWrapper.getSplits("TSLA", "2022-08-02")).willReturn(List.of(
      new SplitDto("2022-08-25", new BigDecimal("3"))));
    given(stockSplitService.apply(eq("TSLA"), any())).willReturn(new Outcome(2, 0));

    assertThat(service.applyAll()).isEqualTo(new Result(2, 1, 0, NOW));
    assertThat(registry.get("trade.split.backfill.trades").counter().count()).isEqualTo(2);
    assertThat(registry.get("trade.split.backfill.failures").tag("kind", "symbol").counter().count()).isEqualTo(1);
  }

  @Test
  @DisplayName("보내지 못한 배당 몫 재계산 요청 수를 결과에 더함")
  void reconcileFailures_Counted() {
    given(tradeRepository.findFirstTradeDateBySymbol()).willReturn(firstTradeDates(
      "NVDA", LocalDate.of(2024, 5, 1), "TSLA", LocalDate.of(2022, 8, 1)));
    given(marketServiceClientWrapper.getSplits("NVDA", "2024-05-02")).willReturn(List.of(
      new SplitDto("2024-06-10", BigDecimal.TEN)));
    given(marketServiceClientWrapper.getSplits("TSLA", "2022-08-02")).willReturn(List.of(
      new SplitDto("2022-08-25", new BigDecimal("3"))));
    given(stockSplitService.apply(eq("NVDA"), any())).willReturn(new Outcome(3, 2));
    given(stockSplitService.apply(eq("TSLA"), any())).willReturn(new Outcome(1, 1));

    assertThat(service.applyAll()).isEqualTo(new Result(4, 0, 3, NOW));
    assertThat(registry.get("trade.split.backfill.failures").tag("kind", "reconcile").counter().count()).isEqualTo(3);
  }

  @Test
  @DisplayName("관리자 시작은 실행기에 넘기고 바로 진행 중 상태, 끝나면 마지막 결과")
  void start_RunsInBackground() {
    given(tradeRepository.findFirstTradeDateBySymbol()).willReturn(Map.of());

    assertThat(service.start()).isEqualTo(new Status(true, null));
    verifyNoInteractions(tradeRepository);

    queued.getFirst().run();

    assertThat(service.status()).isEqualTo(new Status(false, new Result(0, 0, 0, NOW)));
  }

  @Test
  @DisplayName("진행 중에 다시 시작하면 실행기에 또 넘기지 않음")
  void start_WhileRunning_NotQueuedAgain() {
    List<Status> during = new ArrayList<>();
    given(tradeRepository.findFirstTradeDateBySymbol()).willAnswer(invocation -> {
      during.add(service.start());
      during.add(service.status());
      return Map.of();
    });

    service.start();
    queued.getFirst().run();

    assertThat(queued).hasSize(1);
    assertThat(during).extracting(Status::running).containsExactly(true, true);
  }

  @Test
  @DisplayName("진행 중에 정오 실행이 겹치면 건너뜀")
  void run_WhileRunning_Skipped() {
    List<Integer> calls = new ArrayList<>();
    given(tradeRepository.findFirstTradeDateBySymbol()).willAnswer(invocation -> {
      calls.add(calls.size());
      if (calls.size() == 1) {
        service.run();
      }
      return Map.of();
    });

    service.run();

    assertThat(calls).hasSize(1);
  }

  @Test
  @DisplayName("평일 정오(서울)에 파드 하나에서 돎")
  void scheduledWeekdayNoonOnOnePod() throws NoSuchMethodException {
    Method method = StockSplitBackfillService.class.getMethod("run");

    assertThat(method.getAnnotation(Scheduled.class).cron()).isEqualTo("${trade.split-backfill.cron:0 0 12 * * MON-FRI}");
    assertThat(method.getAnnotation(Scheduled.class).zone()).isEqualTo("Asia/Seoul");
    assertThat(method.getAnnotation(SchedulerLock.class).name()).isEqualTo(StockSplitBackfillService.LOCK_NAME);
  }
}
