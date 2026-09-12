package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.api.controller.AdminMarketController.RefreshAllResponse;
import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.AssetService;
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

  @InjectMocks
  private AdminMarketController controller;

  private static Asset asset(String symbol) {
    return Asset.builder().symbol(symbol).name(symbol).build();
  }

  @Test
  @DisplayName("활성 종목마다 1970-01-01 부터 발행하고 실행 시각과 건수를 남긴다")
  void 전체_발행과_기록() {
    Asset aapl = asset("AAPL");
    Asset nvda = asset("NVDA");
    given(assetRepository.findByActiveTrue()).willReturn(List.of(aapl, nvda));
    given(adminJobRunRepository.save(any(AdminJobRun.class))).willAnswer(inv -> inv.getArgument(0));

    RefreshAllResponse body = controller.refreshAllCandles().getBody();

    verify(assetEventProducer).publishAssetCreated(eq(aapl), eq(true), eq(FULL_FROM), any(LocalDate.class), eq(false));
    verify(assetEventProducer).publishAssetCreated(eq(nvda), eq(true), eq(FULL_FROM), any(LocalDate.class), eq(false));
    verify(assetRepository, never()).findAll();

    ArgumentCaptor<AdminJobRun> saved = ArgumentCaptor.forClass(AdminJobRun.class);
    verify(adminJobRunRepository).save(saved.capture());
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
}
