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
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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

  @Mock
  private CandleService candleService;

  @InjectMocks
  private SymbolCollector symbolCollector;

  @BeforeEach
  void setUp() {
    // @Value 필드 직접 주입(스프링 없이는 안 들어감)
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

  private static Asset listed(String symbol, String name, String type) {
    return Asset.builder().symbol(symbol).name(name).sourceName(name).country("US")
      .currency("USD").assetType(type).build();
  }

  // 거른 목록과 거르기 전 심볼(거른 목록 + 이름으로 빠진 심볼)
  private static SymbolCatalog.Listing listing(List<Asset> assets, String... filteredOut) {
    Set<String> listed = Stream.concat(assets.stream().map(Asset::getSymbol), Stream.of(filteredOut))
      .collect(Collectors.toSet());
    return new SymbolCatalog.Listing(assets, listed, true);
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
    @DisplayName("기존 종목이 있으면 기동 시 수집을 발행하지 않는다")
    void 기동_발행_없음() {
      given(assetRepository.count()).willReturn(3L);
      // 신규 조회는 비어 있는 경우
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of()));

      symbolCollector.collectSymbols();

      verify(assetEventProducer, never())
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
      // 신규가 없으면 저장 없음
      verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 종목이 있어도 상장 목록을 확인한다")
    void 신규_확인() {
      given(assetRepository.count()).willReturn(2L);
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of()));

      symbolCollector.collectSymbols();

      verify(symbolCatalog).fetchListing();
    }

    @Test
    @DisplayName("DB 에 없는 심볼만 저장한다")
    void 신규만_저장() {
      given(assetRepository.count()).willReturn(2L);
      given(assetRepository.findAll()).willReturn(assets(2));   // SYM0, SYM1
      given(symbolCatalog.fetchListing())
        .willReturn(listing(List.of(asset("SYM0", 1L), asset("SYM1", 2L), asset("NEWCO", 3L))));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository, times(1)).save(saved.capture());
      org.assertj.core.api.Assertions.assertThat(saved.getValue().getSymbol()).isEqualTo("NEWCO");

      // 신규 1건만(기존 종목은 기동 때 발행 안 함)
      verify(assetEventProducer, times(1))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("상장 목록 이름이 바뀐 종목은 이름 · 유형을 따라 고친다")
    void 목록_이름_갱신() {
      Asset crco = listed("CRCO", "Carolco Pictures", "EQUITY");
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(List.of(crco));
      given(symbolCatalog.fetchListing())
        .willReturn(listing(List.of(listed("CRCO", "YieldMax CRCO Option Income ETF", "ETF"))));

      symbolCollector.collectSymbols();

      assertThat(crco.getName()).isEqualTo("YieldMax CRCO Option Income ETF");
      assertThat(crco.getAssetType()).isEqualTo("ETF");
      assertThat(crco.getSourceName()).isEqualTo("YieldMax CRCO Option Income ETF");
      verify(assetRepository).saveAll(List.of(crco));
      verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자가 고친 이름은 두고 목록 이름만 갱신한다")
    void 손_수정_이름_보호() {
      Asset crco = listed("CRCO", "Carolco Pictures", "EQUITY");
      crco.setName("카롤코 픽처스");
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(List.of(crco));
      given(symbolCatalog.fetchListing())
        .willReturn(listing(List.of(listed("CRCO", "YieldMax CRCO Option Income ETF", "ETF"))));

      symbolCollector.collectSymbols();

      assertThat(crco.getName()).isEqualTo("카롤코 픽처스");
      assertThat(crco.getAssetType()).isEqualTo("EQUITY");
      assertThat(crco.getSourceName()).isEqualTo("YieldMax CRCO Option Income ETF");
      verify(assetRepository).saveAll(List.of(crco));
    }

    @Test
    @DisplayName("목록 이름이 없던 종목은 목록 이름만 채운다")
    void 목록_이름_없던_종목() {
      Asset manual = asset("MYCO", 1L);
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(List.of(manual));
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(listed("MYCO", "My Company Inc", "EQUITY"))));

      symbolCollector.collectSymbols();

      assertThat(manual.getName()).isEqualTo("MYCO");
      assertThat(manual.getSourceName()).isEqualTo("My Company Inc");
      verify(assetRepository).saveAll(List.of(manual));
    }

    @Test
    @DisplayName("목록 이름이 같으면 저장하지 않는다")
    void 목록_이름_같음() {
      Asset apple = listed("AAPL", "Apple Inc", "EQUITY");
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(List.of(apple));
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(listed("AAPL", "Apple Inc", "EQUITY"))));

      symbolCollector.collectSymbols();

      verify(assetRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("목록 조회가 비면 기존 갱신은 그대로 끝난다")
    void 목록_빔() {
      given(assetRepository.count()).willReturn(2L);
      // 목록 API 가 200 에 빈 결과를 주는 경우
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of()));

      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetRepository, never()).save(any());
      verify(assetEventProducer, never())
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("목록에 같은 심볼이 두 번 있어도 한 번만 저장한다")
    void 중복_심볼() {
      // 카탈로그가 중복을 거르지만 한 번 더 확인
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(assets(1));   // SYM0
      given(symbolCatalog.fetchListing())
        .willReturn(listing(List.of(asset("NEWCO", 1L), asset("NEWCO", 1L), asset("OTHER", 2L))));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository, times(2)).save(saved.capture());
      assertThat(saved.getAllValues()).extracting(Asset::getSymbol)
        .containsExactly("NEWCO", "OTHER");
    }

    @Test
    @DisplayName("일일 스케줄은 기존 종목 수를 세지 않고 신규 종목을 저장한다")
    void 일일_스케줄() {
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(asset("NEWCO", 1L))));
      given(assetRepository.findAll()).willReturn(assets(2));   // SYM0, SYM1
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectNewlyListedDaily();

      // 신규 1건만 저장하고 이벤트도 1건
      verify(assetRepository, times(1)).save(any());
      verify(assetEventProducer, times(1))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
      // 기동 경로와 달리 count() 분기 없음
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
    @DisplayName("목록 조회가 실패해도 예외가 새지 않는다")
    void 목록_예외() {
      given(assetRepository.count()).willReturn(2L);
      given(symbolCatalog.fetchListing()).willThrow(new IllegalStateException("LISTING_STATUS 실패"));

      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetEventProducer, never())
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("신규 종목 수집 기간은 lookbackDays 만큼 거슬러 잡는다")
    void 수집_기간() {
      setConfig(true, 30, 0);
      given(assetRepository.count()).willReturn(1L);
      given(assetRepository.findAll()).willReturn(assets(1));
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(asset("NEWCO", 1L))));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
      verify(assetEventProducer)
        .publishAssetCreated(any(), eq(true), from.capture(), to.capture(), eq(true));

      LocalDate today = LocalDate.now();
      org.assertj.core.api.Assertions.assertThat(to.getValue()).isEqualTo(today);
      org.assertj.core.api.Assertions.assertThat(from.getValue()).isEqualTo(today.minusDays(30));
    }

  }

  @Nested
  @DisplayName("상장 목록에서 빠진 종목")
  class Delisting {

    private static final LocalDate EARLIER = LocalDate.of(2025, 1, 2);

    private static Asset apple() {
      return listed("AAPL", "Apple Inc", "EQUITY");
    }

    private static Asset msft() {
      return listed("MSFT", "Microsoft Corp", "EQUITY");
    }

    private static List<Asset> listedAssets(int count) {
      return IntStream.range(0, count)
        .mapToObj(i -> listed("SYM" + i, "Symbol " + i, "EQUITY"))
        .toList();
    }

    @Test
    @DisplayName("거르기 전 목록에 없는 활성 종목을 내리고 상장폐지일을 오늘로 저장한다")
    void 빠진_종목_내림() {
      Asset apple = apple();
      Asset msft = msft();
      Asset blfs = listed("BLFS", "BioLife Solutions Inc", "EQUITY");
      given(assetRepository.findAll()).willReturn(List.of(apple, msft, blfs));
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(apple(), msft())));

      symbolCollector.collectNewlyListedDaily();

      assertThat(blfs.getActive()).isFalse();
      assertThat(blfs.getDelistedDate()).isEqualTo(LocalDate.now());
      assertThat(apple.getActive()).isTrue();
      assertThat(msft.getActive()).isTrue();
      verify(assetRepository).saveAll(List.of(blfs));
      verify(candleService).evictDividendYieldOrder();
    }

    @Test
    @DisplayName("기동 때 확인도 신규 종목을 저장하고 빠진 종목을 내린다")
    void 기동_신규와_내림() {
      Asset apple = apple();
      Asset blfs = listed("BLFS", "BioLife Solutions Inc", "EQUITY");
      given(assetRepository.count()).willReturn(2L);
      given(assetRepository.findAll()).willReturn(List.of(apple, blfs));
      given(symbolCatalog.fetchListing())
        .willReturn(listing(List.of(apple(), listed("NEWCO", "New Co", "EQUITY"))));
      given(assetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

      symbolCollector.collectSymbols();

      ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
      verify(assetRepository).save(saved.capture());
      assertThat(saved.getValue().getSymbol()).isEqualTo("NEWCO");
      assertThat(blfs.getActive()).isFalse();
      verify(assetRepository).saveAll(List.of(blfs));
    }

    @Test
    @DisplayName("이름으로 걸러진 종목도 거르기 전 목록에 있으면 그대로 둔다")
    void 이름_필터_종목_유지() {
      Asset warrant = listed("EVOXW", "Evolution Global Acquisition Corp - Warrants", "EQUITY");
      given(assetRepository.findAll()).willReturn(List.of(apple(), warrant));
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(apple()), "EVOXW"));

      symbolCollector.collectNewlyListedDaily();

      assertThat(warrant.getActive()).isTrue();
      verify(assetRepository, never()).saveAll(any());
      verify(candleService, never()).evictDividendYieldOrder();
    }

    @Test
    @DisplayName("목록 이름이 없는 종목은 목록에 없어도 그대로 둔다")
    void 목록_이름_없음_유지() {
      Asset manual = asset("005930.KS", null);
      given(assetRepository.findAll()).willReturn(List.of(apple(), manual));
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(apple())));

      symbolCollector.collectNewlyListedDaily();

      assertThat(manual.getActive()).isTrue();
      assertThat(manual.getDelistedDate()).isNull();
      verify(assetRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("거르기 전 목록이 활성 종목의 절반 미만이면 아무것도 내리지 않는다")
    void 절반_미만_중단() {
      List<Asset> stored = listedAssets(10);
      given(assetRepository.findAll()).willReturn(stored);
      given(symbolCatalog.fetchListing()).willReturn(listing(listedAssets(4)));

      symbolCollector.collectNewlyListedDaily();

      assertThat(stored).allMatch(Asset::getActive);
      verify(assetRepository, never()).saveAll(any());
      verify(candleService, never()).evictDividendYieldOrder();
    }

    @Test
    @DisplayName("거르기 전 목록이 활성 종목의 절반이면 빠진 종목을 내린다")
    void 절반_내림() {
      List<Asset> stored = listedAssets(10);
      given(assetRepository.findAll()).willReturn(stored);
      given(symbolCatalog.fetchListing()).willReturn(listing(listedAssets(5)));

      symbolCollector.collectNewlyListedDaily();

      assertThat(stored).filteredOn(a -> !a.getActive()).extracting(Asset::getSymbol)
        .containsExactly("SYM5", "SYM6", "SYM7", "SYM8", "SYM9");
    }

    @Test
    @DisplayName("이미 내려간 종목은 그대로 두고 활성 수에도 세지 않는다")
    void 내려간_종목() {
      Asset msft = msft();
      List<Asset> gone = listedAssets(3);
      gone.forEach(a -> {
        a.setActive(false);
        a.setDelistedDate(EARLIER);
      });
      List<Asset> stored = Stream.concat(Stream.of(apple(), msft), gone.stream()).toList();
      given(assetRepository.findAll()).willReturn(stored);
      given(symbolCatalog.fetchListing()).willReturn(listing(List.of(apple())));

      symbolCollector.collectNewlyListedDaily();

      assertThat(msft.getActive()).isFalse();
      assertThat(gone).extracting(Asset::getDelistedDate).containsOnly(EARLIER);
      verify(assetRepository).saveAll(List.of(msft));
    }

    @Test
    @DisplayName("상한에서 잘렸거나 번들 CSV 로 대체한 목록이면 내리지 않는다")
    void 전체_목록_아님() {
      Asset blfs = listed("BLFS", "BioLife Solutions Inc", "EQUITY");
      given(assetRepository.findAll()).willReturn(List.of(apple(), msft(), blfs));
      given(symbolCatalog.fetchListing())
        .willReturn(new SymbolCatalog.Listing(List.of(apple(), msft()), Set.of("AAPL", "MSFT"), false));

      symbolCollector.collectNewlyListedDaily();

      assertThat(blfs.getActive()).isTrue();
      verify(assetRepository, never()).saveAll(any());
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

      // 실패한 1건은 이벤트도 없음
      verify(assetEventProducer, times(2))
        .publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("소스 조회가 전부 실패해도 예외를 밖으로 내보내지 않는다")
    void 소스_실패() {
      given(assetRepository.count()).willReturn(0L);
      given(symbolCatalog.fetchAll()).willThrow(new IllegalStateException("LISTING_STATUS 실패"));

      // 부팅 때 이벤트 리스너라 예외를 밖으로 안 냄(기동 로그 오염)
      assertThatCode(() -> symbolCollector.collectSymbols()).doesNotThrowAnyException();

      verify(assetRepository, never()).save(any());
    }
  }
}
