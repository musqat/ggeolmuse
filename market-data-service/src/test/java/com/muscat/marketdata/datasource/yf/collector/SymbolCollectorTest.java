package com.muscat.marketdata.datasource.yf.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.marketdata.datasource.common.SymbolCatalog;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SymbolCollector 단위 테스트")
class SymbolCollectorTest {

  @Mock
  private SymbolCatalog symbolCatalog;

  @Mock
  private AssetRepository assetRepository;

  @Mock
  private AssetEventProducer assetEventProducer;

  @InjectMocks
  private SymbolCollector symbolCollector;

  @BeforeEach
  void setUp() {
    // @Value 필드는 스프링 없이 주입되지 않아 직접 넣는다
    setConfig(true, 365, 0);
  }

  private void setConfig(boolean enabled, int lookbackDays, int maxSymbols) {
    ReflectionTestUtils.setField(symbolCollector, "enabled", enabled);
    ReflectionTestUtils.setField(symbolCollector, "lookbackDays", lookbackDays);
    ReflectionTestUtils.setField(symbolCollector, "maxCollectSymbols", maxSymbols);
  }

  private static Asset asset(String symbol, Long marketCap) {
    return Asset.builder().symbol(symbol).name(symbol).country("US")
      .currency("USD").assetType("EQUITY").marketCap(marketCap).build();
  }

  private static List<Asset> assets(int count) {
    return IntStream.range(0, count)
      .mapToObj(i -> asset("SYM" + i, (long) i))
      .toList();
  }

  @Nested
  @DisplayName("비활성화")
  class Disabled {

    @Test
    @DisplayName("enabled=false 면 저장소도 건드리지 않는다")
    void 비활성화() {
      setConfig(false, 365, 0);

      symbolCollector.collectSymbols();

      verifyNoInteractions(assetRepository, symbolCatalog, assetEventProducer);
    }
  }

  @Nested
  @DisplayName("기존 종목이 있을 때")
  class ExistingSymbols {

    @Test
    @DisplayName("기존 종목 전부에 수집 이벤트를 발행한다")
    void 업데이트() {
      given(assetRepository.count()).willReturn(3L);
      given(assetRepository.findAll()).willReturn(assets(3));
      // 신규 조회는 비어 있는 경우
      given(symbolCatalog.fetchAll()).willReturn(List.of());

      symbolCollector.collectSymbols();

      verify(assetEventProducer, times(3))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
      // 신규가 없으면 저장은 일어나지 않는다
      verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 종목이 있어도 신규 상장을 확인한다")
    void 신규_확인() {
      given(assetRepository.count()).willReturn(2L);
      given(assetRepository.findAll()).willReturn(assets(2));
      given(symbolCatalog.fetchAll()).willReturn(List.of());

      symbolCollector.collectSymbols();

      // 예전에는 여기서 바로 return 해서 목록 조회 자체를 안 했다
      verify(symbolCatalog).fetchAll();
    }

    @Test
    @DisplayName("DB 에 없는 심볼만 저장한다")
    void 신규만_저장() {
      given(assetRepository.count()).willReturn(2L);
      given(assetRepository.findAll()).willReturn(assets(2));   // SYM0, SYM1
      given(symbolCatalog.fetchAll())
        .willReturn(List.of(asset("SYM0", 1L), asset("SYM1", 2L), asset("NEWCO", 3L)));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository, times(1)).save(saved.capture());
      org.assertj.core.api.Assertions.assertThat(saved.getValue().getSymbol()).isEqualTo("NEWCO");

      // 기존 2건 + 신규 1건
      verify(assetEventProducer, times(3))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("목록 조회가 비면 기존 갱신은 그대로 끝난다")
    void 목록_빔() {
      given(assetRepository.count()).willReturn(2L);
      given(assetRepository.findAll()).willReturn(assets(2));
      // NASDAQ 이 200 에 빈 결과를 주는 경우
      given(symbolCatalog.fetchAll()).willReturn(List.of());

      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetRepository, never()).save(any());
      verify(assetEventProducer, times(2))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("목록에 같은 심볼이 두 번 있어도 한 번만 저장한다")
    void 중복_심볼() {
      // 카탈로그가 중복을 걸러 주지만 방어적으로 한 번 더 본다
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(assets(1));   // SYM0
      given(symbolCatalog.fetchAll())
        .willReturn(List.of(asset("NEWCO", 1L), asset("NEWCO", 1L), asset("OTHER", 2L)));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository, times(2)).save(saved.capture());
      assertThat(saved.getAllValues()).extracting(Asset::getSymbol)
        .containsExactly("NEWCO", "OTHER");
    }

    @Test
    @DisplayName("일일 스케줄은 기존 종목 갱신 없이 신규만 확인한다")
    void 일일_스케줄() {
      given(symbolCatalog.fetchAll())
        .willReturn(List.of(asset("NEWCO", 1L)));
      given(assetRepository.findAll()).willReturn(assets(2));   // SYM0, SYM1
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectNewlyListedDaily();

      // 신규 1건만 저장하고 이벤트도 1건
      verify(assetRepository, times(1)).save(any());
      verify(assetEventProducer, times(1))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
      // 기동 경로와 달리 count() 로 분기하지 않는다
      verify(assetRepository, never()).count();
    }

    @Test
    @DisplayName("일일 스케줄도 비활성화면 아무것도 하지 않는다")
    void 일일_비활성화() {
      setConfig(false, 365, 0);

      symbolCollector.collectNewlyListedDaily();

      verifyNoInteractions(assetRepository, symbolCatalog, assetEventProducer);
    }

    @Test
    @DisplayName("목록 조회가 실패해도 기존 갱신은 유지된다")
    void 목록_예외() {
      given(assetRepository.count()).willReturn(2L);
      given(assetRepository.findAll()).willReturn(assets(2));
      given(symbolCatalog.fetchAll()).willThrow(new IllegalStateException("NASDAQ 다운"));

      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetEventProducer, times(2))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("수집 기간은 lookbackDays 만큼 거슬러 잡는다")
    void 수집_기간() {
      setConfig(true, 30, 0);
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(assets(1));

      symbolCollector.collectSymbols();

      ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
      verify(assetEventProducer)
        .publishAssetCreated(any(), eq(true), from.capture(), to.capture(), eq(true));

      LocalDate today = LocalDate.now();
      org.assertj.core.api.Assertions.assertThat(to.getValue()).isEqualTo(today);
      org.assertj.core.api.Assertions.assertThat(from.getValue()).isEqualTo(today.minusDays(30));
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 발행한다")
    void 부분_실패() {
      given(assetRepository.count()).willReturn(3L);
      given(assetRepository.findAll()).willReturn(assets(3));
      lenient().doThrow(new IllegalStateException("발행 실패"))
        .when(assetEventProducer)
        .publishAssetCreated(eq(assets(3).get(1)), anyBoolean(), any(), any(), anyBoolean());

      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetEventProducer, times(3))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }
  }

  @Nested
  @DisplayName("기존 종목이 없을 때")
  class NewSymbols {

    @Test
    @DisplayName("소스에서 받아 저장하고 이벤트를 발행한다")
    void 신규_로드() {
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(assets(3));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      verify(assetRepository, times(3)).save(any());
      verify(assetEventProducer, times(3))
        .publishAssetCreated(any(), eq(true), any(), any(), eq(true));
    }

    @Test
    @DisplayName("소스가 빈 목록이면 저장하지 않는다")
    void 종목_없음() {
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(List.of());

      symbolCollector.collectSymbols();

      verify(assetRepository, never()).save(any());
      verifyNoInteractions(assetEventProducer);
    }

    @Test
    @DisplayName("상한이 0이면 전부 수집한다")
    void 상한_없음() {
      setConfig(true, 365, 0);
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(assets(5));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      verify(assetRepository, times(5)).save(any());
    }

    @Test
    @DisplayName("상한보다 적으면 자르지 않는다")
    void 상한_미만() {
      setConfig(true, 365, 10);
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(assets(5));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      verify(assetRepository, times(5)).save(any());
    }

    @Test
    @DisplayName("상한을 넘으면 시가총액 상위만 남긴다")
    void 상한_적용() {
      setConfig(true, 365, 2);
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(List.of(
        asset("SMALL", 100L), asset("BIG", 9000L), asset("MID", 500L)));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository, times(2)).save(saved.capture());
      org.assertj.core.api.Assertions.assertThat(saved.getAllValues())
        .extracting(Asset::getSymbol)
        .containsExactly("BIG", "MID");
    }

    @Test
    @DisplayName("시가총액이 null 이면 0으로 보고 뒤로 민다")
    void 시총_null() {
      setConfig(true, 365, 2);
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(List.of(
        asset("NULLCAP", null), asset("BIG", 9000L), asset("MID", 500L)));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository, times(2)).save(saved.capture());
      org.assertj.core.api.Assertions.assertThat(saved.getAllValues())
        .extracting(Asset::getSymbol)
        .containsExactly("BIG", "MID");
    }

    @Test
    @DisplayName("한 종목 저장이 실패해도 나머지는 계속 저장한다")
    void 부분_실패() {
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willReturn(assets(3));
      given(assetRepository.save(any())).willAnswer(inv -> {
        Asset a = inv.getArgument(0);
        if ("SYM1".equals(a.getSymbol())) {
          throw new IllegalStateException("중복 키");
        }
        return a;
      });

      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      // 실패한 1건은 이벤트도 안 나간다
      verify(assetEventProducer, times(2))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("소스 조회가 통째로 실패해도 예외를 밖으로 내보내지 않는다")
    void 소스_실패() {
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willThrow(new IllegalStateException("NASDAQ 다운"));

      // 부팅 시 이벤트 리스너라 예외가 나가면 기동 로그가 지저분해진다
      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetRepository, never()).save(any());
    }
  }
}
