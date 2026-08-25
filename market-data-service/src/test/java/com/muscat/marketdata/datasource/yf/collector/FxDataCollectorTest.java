package com.muscat.marketdata.datasource.yf.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FxDataCollector 단위 테스트")
class FxDataCollectorTest {

  // 2024-09-16(월) ~ 09-22(일). PREV_FRI 는 MON 의 이전 영업일
  private static final LocalDate PREV_FRI = LocalDate.of(2024, 9, 13);
  private static final LocalDate MON = LocalDate.of(2024, 9, 16);
  private static final LocalDate FRI = LocalDate.of(2024, 9, 20);
  private static final LocalDate SAT = LocalDate.of(2024, 9, 21);
  private static final LocalDate SUN = LocalDate.of(2024, 9, 22);

  @Mock
  private FxSource fxSource;

  @Mock
  private FxRateRepository fxRateRepository;

  private MarketDataProperties properties;
  private FxDataCollector collector;

  @BeforeEach
  void setUp() {
    // 실제 프로퍼티 객체를 쓰면 중첩 구조를 목으로 흉내 낼 필요가 없다
    properties = new MarketDataProperties();
    properties.getFx().setMaxFallbackDays(2);
    properties.getFxIngest().getBackfill().setEnabled(false);
    properties.getFxIngest().getIncremental().setEnabled(false);

    collector = new FxDataCollector(fxSource, fxRateRepository, properties);
  }

  @Nested
  @DisplayName("collectSingleDate")
  class CollectSingleDate {

    @Test
    @DisplayName("환율을 소수 여섯 자리로 맞춰 담는다")
    void 정상() {
      given(fxSource.fetchFx(MON)).willReturn(Optional.of(new BigDecimal("1350.1234567")));

      FxRate result = collector.collectSingleDate(MON, false).orElseThrow();

      assertThat(result.getDate()).isEqualTo(MON);
      assertThat(result.getRate()).isEqualByComparingTo("1350.123457");
      assertThat(result.getRate().scale()).isEqualTo(6);
      assertThat(result.getCurrencyPair()).isEqualTo("USD/KRW");
    }

    @Test
    @DisplayName("폴백을 끄면 한 번만 조회하고 비어서 돌아온다")
    void 폴백_off() {
      given(fxSource.fetchFx(MON)).willReturn(Optional.empty());

      assertThat(collector.collectSingleDate(MON, false)).isEmpty();

      verify(fxSource).fetchFx(MON);
      verify(fxSource, never()).fetchFx(MON.minusDays(1));
    }

    @Test
    @DisplayName("데이터가 없으면 이전 영업일로 거슬러 올라간다")
    void 폴백_성공() {
      // 월요일 데이터가 없으면 토·일을 건너뛰고 직전 금요일을 본다
      given(fxSource.fetchFx(MON)).willReturn(Optional.empty());
      given(fxSource.fetchFx(PREV_FRI)).willReturn(Optional.of(new BigDecimal("1340")));

      FxRate result = collector.collectSingleDate(MON, true).orElseThrow();

      // 금요일 값을 가져와도 저장 날짜는 원래 요청한 월요일이다
      assertThat(result.getDate()).isEqualTo(MON);
      assertThat(result.getRate()).isEqualByComparingTo("1340");
    }

    @Test
    @DisplayName("소스가 예외를 던져도 폴백을 계속한다")
    void 예외_후_폴백() {
      given(fxSource.fetchFx(MON)).willThrow(new IllegalStateException("API 오류"));
      given(fxSource.fetchFx(PREV_FRI)).willReturn(Optional.of(new BigDecimal("1340")));

      assertThat(collector.collectSingleDate(MON, true)).isPresent();
    }

    @Test
    @DisplayName("폴백 한도를 넘으면 예외를 던진다")
    void 폴백_초과() {
      given(fxSource.fetchFx(any())).willReturn(Optional.empty());

      // 빈 Optional 이 아니라 예외로 끝난다. 호출부가 이걸 안 잡으면 배치 전체가 멈춘다
      assertThatThrownBy(() -> collector.collectSingleDate(MON, true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("환율 폴백 기간 초과");
    }
  }

  @Nested
  @DisplayName("collectDateRange")
  class CollectDateRange {

    @Test
    @DisplayName("주말은 아예 조회하지 않는다")
    void 주말_제외() {
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      List<FxRate> result = collector.collectDateRange(FRI, SUN);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getDate()).isEqualTo(FRI);
      verify(fxSource, never()).fetchFx(SAT);
      verify(fxSource, never()).fetchFx(SUN);
    }

    @Test
    @DisplayName("평일을 하루씩 훑는다")
    void 평일_전체() {
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      List<FxRate> result = collector.collectDateRange(MON, FRI);

      assertThat(result).hasSize(5);
      assertThat(result).extracting(FxRate::getDate)
        .containsExactly(MON, MON.plusDays(1), MON.plusDays(2), MON.plusDays(3), FRI);
    }

    @Test
    @DisplayName("시작일이 종료일보다 뒤면 빈 목록")
    void 역순_범위() {
      assertThat(collector.collectDateRange(FRI, MON)).isEmpty();
      verify(fxSource, never()).fetchFx(any());
    }

    @Test
    @DisplayName("주말만 있으면 빈 목록")
    void 주말만() {
      assertThat(collector.collectDateRange(SAT, SUN)).isEmpty();
    }
  }

  @Nested
  @DisplayName("run")
  class Run {

    @Test
    @DisplayName("둘 다 꺼져 있으면 저장하지 않는다")
    void 전부_비활성() {
      collector.run();

      verify(fxRateRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("과거 수집만 켜면 backfill 만 돈다")
    void 과거만() {
      properties.getFxIngest().getBackfill().setEnabled(true);
      properties.getFxIngest().getBackfill().setLookbackDays(2);
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      collector.run();

      verify(fxRateRepository).saveAll(any());
      // 증분 경로에서만 쓰는 findAll 은 호출되지 않는다
      verify(fxRateRepository, never()).findAll();
    }

    @Test
    @DisplayName("증분 수집만 켜면 마지막 날짜부터 이어간다")
    void 증분만() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      // 마지막 저장일을 일주일 전으로 둔다. 하루 전으로 잡으면 수집 범위가
      // 오늘 하루뿐이라, 오늘이 주말이면 fetchFx 가 한 번도 불리지 않아
      // 테스트가 요일에 따라 깨진다.
      LocalDate lastSaved = LocalDate.now().minusDays(7);
      given(fxRateRepository.findAll())
        .willReturn(List.of(FxRate.builder().date(lastSaved).rate(BigDecimal.ONE).build()));
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      collector.run();

      verify(fxRateRepository).saveAll(any());
      // 일주일이면 평일이 반드시 들어간다
      verify(fxSource, atLeastOnce()).fetchFx(any());
    }

    @Test
    @DisplayName("이미 오늘까지 있으면 증분 수집을 건너뛴다")
    void 증분_스킵() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      given(fxRateRepository.findAll())
        .willReturn(List.of(FxRate.builder().date(LocalDate.now()).rate(BigDecimal.ONE).build()));

      collector.run();

      verify(fxRateRepository, never()).saveAll(any());
      verify(fxSource, never()).fetchFx(any());
    }

    @Test
    @DisplayName("저장된 게 없으면 defaultDays 만큼 거슬러 수집한다")
    void 증분_최초() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      properties.getFxIngest().getIncremental().setDefaultDays(3);
      given(fxRateRepository.findAll()).willReturn(List.of());
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      collector.run();

      ArgumentCaptor<List<FxRate>> saved = ArgumentCaptor.forClass(List.class);
      verify(fxRateRepository).saveAll(saved.capture());
      assertThat(saved.getValue()).isNotEmpty();
      assertThat(saved.getValue())
        .allSatisfy(r -> assertThat(r.getDate())
          .isAfterOrEqualTo(LocalDate.now().minusDays(3))
          .isBeforeOrEqualTo(LocalDate.now()));
    }

    @Test
    @DisplayName("수집이 실패해도 예외를 밖으로 내보내지 않는다")
    void 실패_삼킴() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      given(fxRateRepository.findAll()).willThrow(new IllegalStateException("DB 다운"));

      // CommandLineRunner 라 예외가 나가면 애플리케이션 기동이 실패한다
      assertThatCode(() -> collector.run()).doesNotThrowAnyException();
    }
  }
}
