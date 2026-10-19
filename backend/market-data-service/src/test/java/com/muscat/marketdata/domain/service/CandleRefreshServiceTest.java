package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 캔들 재수집")
class CandleRefreshServiceTest {

  private static final String JOB = "candle-refresh-all";

  @Mock private AssetRepository assetRepository;
  @Mock private AssetEventProducer assetEventProducer;
  @Mock private AdminJobRunRepository adminJobRunRepository;
  @Mock private CandleRefreshAllPublisher refreshAllPublisher;

  @InjectMocks private CandleRefreshService service;

  private static Asset asset(String symbol, boolean active) {
    return Asset.builder().symbol(symbol).name(symbol).active(active).build();
  }

  @Test
  @DisplayName("심볼을 다듬어 대문자로 찾고, 활성 종목만 배당까지 발행하며, 비활성은 세지 않고 없는 종목은 따로 돌려준다")
  void refresh_PublishesActiveOnly() {
    Asset aapl = asset("AAPL", true);
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(aapl));
    given(assetRepository.findById("DEAD")).willReturn(Optional.of(asset("DEAD", false)));
    given(assetRepository.findById("GONE")).willReturn(Optional.empty());

    CandleRefreshService.RefreshResult result = service.refresh(List.of(" aapl ", "DEAD", "gone"), LocalDate.of(2020, 1, 1));

    assertThat(result.requested()).isEqualTo(3);
    assertThat(result.published()).isEqualTo(1);
    assertThat(result.notFound()).containsExactly("GONE");
    assertThat(result.from()).isEqualTo(LocalDate.of(2020, 1, 1));
    assertThat(result.to()).isEqualTo(LocalDate.now(ZoneId.of("America/New_York")));
    verify(assetEventProducer).publishAssetCreated(aapl, true, LocalDate.of(2020, 1, 1), result.to(), true);
    verify(assetEventProducer, never()).publishAssetCreated(argThat(a -> a.getSymbol().equals("DEAD")), anyBoolean(), any(), any(), anyBoolean());
  }

  @Test
  @DisplayName("시작일이 없으면 1970-01-01 부터, 종목이 없으면 아무것도 발행하지 않는다")
  void refresh_Defaults() {
    CandleRefreshService.RefreshResult result = service.refresh(null, null);

    assertThat(result.requested()).isZero();
    assertThat(result.from()).isEqualTo(LocalDate.of(1970, 1, 1));
    verifyNoInteractions(assetEventProducer);
  }

  @Test
  @DisplayName("전체 재수집은 실행 시각과 활성 종목 수를 먼저 기록하고 발행은 뒤로 넘긴다")
  void refreshAll_RecordsThenPublishes() {
    List<Asset> active = List.of(asset("AAPL", true), asset("NVDA", true));
    given(assetRepository.findByActiveTrue()).willReturn(active);
    given(adminJobRunRepository.save(any(AdminJobRun.class))).willAnswer(invocation -> invocation.getArgument(0));

    AdminJobRun run = service.refreshAll();

    InOrder order = inOrder(adminJobRunRepository, refreshAllPublisher);
    order.verify(adminJobRunRepository).save(run);
    order.verify(refreshAllPublisher).publish(eq(active), eq(LocalDate.of(1970, 1, 1)), any(LocalDate.class));
    verifyNoInteractions(assetEventProducer);
    assertThat(run.getName()).isEqualTo(JOB);
    assertThat(run.getPublished()).isEqualTo(2);
    assertThat(run.getLastRunAt()).isNotNull();
  }

  @Test
  @DisplayName("마지막 전체 재수집 기록은 있으면 그대로, 없으면 비어 있다")
  void lastRefreshAll() {
    AdminJobRun run = new AdminJobRun(JOB, LocalDateTime.of(2026, 9, 12, 14, 3), 7812);
    given(adminJobRunRepository.findById(JOB)).willReturn(Optional.of(run)).willReturn(Optional.empty());

    assertThat(service.lastRefreshAll()).contains(run);
    assertThat(service.lastRefreshAll()).isEmpty();
  }
}
