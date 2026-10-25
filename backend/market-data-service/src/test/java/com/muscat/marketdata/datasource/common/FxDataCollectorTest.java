package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
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

  // 수집기와 같은 KST 오늘. JVM 기본 타임존이면 UTC 러너에서 하루 어긋남
  private static LocalDate today() {
    return LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
  }

  // from ~ to 의 평일
  private static List<LocalDate> weekdays(LocalDate from, LocalDate to) {
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
        days.add(d);
      }
    }
    return days;
  }

  // 그 날짜들에 저장된 환율 행
  private static List<FxRate> stored(List<LocalDate> dates) {
    return dates.stream().map(d -> FxRate.builder().date(d).rate(BigDecimal.ONE).build()).toList();
  }

  // saveAll 로 저장한 날짜
  @SuppressWarnings("unchecked")
  private List<LocalDate> savedDates() {
    ArgumentCaptor<List<FxRate>> saved = ArgumentCaptor.forClass(List.class);
    verify(fxRateRepository).saveAll(saved.capture());
    return saved.getValue().stream().map(FxRate::getDate).toList();
  }

  @Mock
  private FxSource fxSource;

  @Mock
  private FxRateRepository fxRateRepository;

  private MarketDataProperties properties;
  private FxDataCollector collector;

  @BeforeEach
  void setUp() {
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
      // 월요일 값이 없으면 토·일을 건너뛰고 직전 금요일 조회
      given(fxSource.fetchFx(MON)).willReturn(Optional.empty());
      given(fxSource.fetchFx(PREV_FRI)).willReturn(Optional.of(new BigDecimal("1340")));

      FxRate result = collector.collectSingleDate(MON, true).orElseThrow();

      // 값은 금요일 것이어도 날짜는 요청한 월요일
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
  @DisplayName("시작 때 채우기")
  class CollectOnStartup {

    @Test
    @DisplayName("둘 다 꺼져 있으면 저장하지 않는다")
    void 전부_비활성() {
      collector.collectOnStartup();

      verify(fxRateRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("과거 수집만 켜면 backfill 만 돈다")
    void 과거만() {
      properties.getFxIngest().getBackfill().setEnabled(true);
      properties.getFxIngest().getBackfill().setLookbackDays(2);
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      collector.collectOnStartup();

      verify(fxRateRepository).saveAll(any());
      // 저장된 날짜는 증분 수집에서만 봄
      verify(fxRateRepository, never()).findByDateBetween(any(), any());
    }

    @Test
    @DisplayName("증분 수집은 시작일 ~ 오늘 평일 중 저장 안 된 날만 받는다(중간 빈 날 포함)")
    void 증분_빈날만() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      LocalDate start = today().minusDays(21);
      properties.getFxIngest().setStartDate(start);
      List<LocalDate> all = weekdays(start, today());
      LocalDate gap = all.get(all.size() / 2);
      given(fxRateRepository.findByDateBetween(start, today()))
        .willReturn(stored(all.stream().filter(d -> !d.equals(gap)).toList()));
      given(fxSource.fetchFx(gap)).willReturn(Optional.of(new BigDecimal("1350")));

      collector.collectOnStartup();

      assertThat(savedDates()).containsExactly(gap);
    }

    @Test
    @DisplayName("빈 날이 시작 때 최대 수보다 많으면 최근 날부터 그만큼만 받는다")
    void 증분_최대수() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      properties.getFxIngest().getIncremental().setDefaultDays(3);
      LocalDate start = today().minusDays(30);
      properties.getFxIngest().setStartDate(start);
      List<LocalDate> all = weekdays(start, today());
      given(fxRateRepository.findByDateBetween(start, today())).willReturn(List.of());
      given(fxSource.fetchFx(any())).willReturn(Optional.of(new BigDecimal("1350")));

      collector.collectOnStartup();

      assertThat(savedDates()).containsExactlyElementsOf(all.subList(all.size() - 3, all.size()));
    }

    @Test
    @DisplayName("빈 날 하나를 못 받아도 나머지 빈 날은 받는다")
    void 증분_하루실패() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      LocalDate start = today().minusDays(21);
      properties.getFxIngest().setStartDate(start);
      List<LocalDate> all = weekdays(start, today());
      LocalDate failing = all.get(2);
      LocalDate gap = all.get(all.size() / 2);
      given(fxRateRepository.findByDateBetween(start, today()))
        .willReturn(stored(all.stream().filter(d -> !d.equals(failing) && !d.equals(gap)).toList()));
      // 폴백 한도(2)를 넘게 비어 있어 그날은 예외
      given(fxSource.fetchFx(any())).willReturn(Optional.empty());
      given(fxSource.fetchFx(eq(gap))).willReturn(Optional.of(new BigDecimal("1350")));

      collector.collectOnStartup();

      assertThat(savedDates()).containsExactly(gap);
    }

    @Test
    @DisplayName("이미 오늘까지 다 있으면 증분 수집을 건너뛴다")
    void 증분_스킵() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      LocalDate start = today().minusDays(10);
      properties.getFxIngest().setStartDate(start);
      given(fxRateRepository.findByDateBetween(start, today())).willReturn(stored(weekdays(start, today())));

      collector.collectOnStartup();

      verify(fxRateRepository, never()).saveAll(any());
      verify(fxSource, never()).fetchFx(any());
    }

    @Test
    @DisplayName("수집이 실패해도 예외를 밖으로 내보내지 않는다")
    void 실패_삼킴() {
      properties.getFxIngest().getIncremental().setEnabled(true);
      given(fxRateRepository.findByDateBetween(any(), any())).willThrow(new IllegalStateException("DB 다운"));

      assertThatCode(() -> collector.collectOnStartup()).doesNotThrowAnyException();
    }
  }
}
