package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.marketdata.api.controller.AdminMarketController.RefreshAllResponse;
import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleRefreshAllPublisher;
import com.muscat.marketdata.domain.service.UnadjustedScanService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 캔들 전체 다시 받기")
class AdminMarketControllerTest {

  private static final LocalDate FULL_FROM = LocalDate.of(1970, 1, 1);
  private static final String JOB = "candle-refresh-all";

  @Mock private AssetService assetService;
  @Mock private AssetRepository assetRepository;
  @Mock private CandleRepository candleRepository;
  @Mock private AssetEventProducer assetEventProducer;
  @Mock private UnadjustedScanService scanService;
  @Mock private ObjectProvider<SymbolCollector> symbolCollectorProvider;
  @Mock private AdminJobRunRepository adminJobRunRepository;
  @Mock private CandleRefreshAllPublisher refreshAllPublisher;

  @InjectMocks
  private AdminMarketController controller;

  private static Asset asset(String symbol) {
    return Asset.builder().symbol(symbol).name(symbol).build();
  }

  @Test
  @DisplayName("실행 시각과 건수를 먼저 남기고 활성 종목 발행은 뒤로 넘긴다")
  void 기록하고_발행은_뒤로() {
    Asset aapl = asset("AAPL");
    Asset nvda = asset("NVDA");
    given(assetRepository.findByActiveTrue()).willReturn(List.of(aapl, nvda));
    given(adminJobRunRepository.save(any(AdminJobRun.class))).willAnswer(inv -> inv.getArgument(0));

    RefreshAllResponse body = controller.refreshAllCandles().getBody();

    InOrder order = inOrder(adminJobRunRepository, refreshAllPublisher);
    ArgumentCaptor<AdminJobRun> saved = ArgumentCaptor.forClass(AdminJobRun.class);
    order.verify(adminJobRunRepository).save(saved.capture());
    order.verify(refreshAllPublisher).publish(eq(List.of(aapl, nvda)), eq(FULL_FROM), any(LocalDate.class));
    verifyNoInteractions(assetEventProducer);
    verify(assetRepository, never()).findAll();

    assertThat(saved.getValue().getName()).isEqualTo(JOB);
    assertThat(saved.getValue().getPublished()).isEqualTo(2);
    assertThat(saved.getValue().getLastRunAt()).isNotNull();

    assertThat(body.getPublished()).isEqualTo(2);
    assertThat(body.getLastRunAt()).isEqualTo(saved.getValue().getLastRunAt());
  }

  @Test
  @DisplayName("기록이 없으면 실행 시각은 비어 있다")
  void 기록_없음() {
    given(adminJobRunRepository.findById(JOB)).willReturn(Optional.empty());

    RefreshAllResponse body = controller.lastRefreshAll().getBody();

    assertThat(body.getLastRunAt()).isNull();
    assertThat(body.getPublished()).isZero();
  }

  @Test
  @DisplayName("기록이 있으면 마지막 실행 시각과 건수를 준다")
  void 기록_있음() {
    LocalDateTime at = LocalDateTime.of(2026, 9, 12, 14, 3);
    given(adminJobRunRepository.findById(JOB)).willReturn(Optional.of(new AdminJobRun(JOB, at, 7812)));

    RefreshAllResponse body = controller.lastRefreshAll().getBody();

    assertThat(body.getLastRunAt()).isEqualTo(at);
    assertThat(body.getPublished()).isEqualTo(7812);
  }

  @Test
  @DisplayName("신규 종목 수집기가 없는 provider 면 503 이고 안내를 준다")
  void collectorMissing() {
    given(symbolCollectorProvider.getIfAvailable()).willReturn(null);

    var response = controller.collectNewSymbols();

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(java.util.Objects.requireNonNull(response.getBody()).getMessage()).contains("yahoo");
  }

  @Test
  @DisplayName("뒤에서 도는 전체 시총 갱신 · 신규 종목 수집이 실패해도 요청은 200 으로 끝난다")
  void backgroundFailures() {
    SymbolCollector collector = org.mockito.Mockito.mock(SymbolCollector.class);
    given(symbolCollectorProvider.getIfAvailable()).willReturn(collector);
    given(assetService.updateAllMarketCaps()).willThrow(new IllegalStateException("source down"));
    org.mockito.BDDMockito.willThrow(new IllegalStateException("blocked")).given(collector).collectNewlyListedDaily();

    assertThat(controller.updateMarketCaps().getStatusCode().value()).isEqualTo(200);
    assertThat(controller.collectNewSymbols().getStatusCode().value()).isEqualTo(200);

    org.mockito.Mockito.verify(assetService, org.mockito.Mockito.timeout(2000)).updateAllMarketCaps();
    org.mockito.Mockito.verify(collector, org.mockito.Mockito.timeout(2000)).collectNewlyListedDaily();
  }
}
