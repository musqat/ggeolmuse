package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("환율 서비스")
class FxRateServiceImplTest {

  private static final LocalDate DATE = LocalDate.of(2024, 3, 4);

  private final FxRateRepository repository = mock(FxRateRepository.class);
  private final FxRateServiceImpl service = new FxRateServiceImpl(repository);

  private static FxRate rate(String value) {
    return rate(DATE, value);
  }

  private static FxRate rate(LocalDate date, String value) {
    return FxRate.builder().date(date).rate(new BigDecimal(value)).build();
  }

  @Test
  @DisplayName("날짜 조회는 그날 이전 가장 가까운 영업일 값")
  void reads() {
    given(repository.findFirstByDateLessThanEqualOrderByDateDesc(DATE)).willReturn(Optional.of(rate("1330")), Optional.empty());

    assertThat(service.findByDate(DATE).getRate()).isEqualByComparingTo("1330");
    assertThat(service.findByDate(DATE)).isNull();
  }

  @Test
  @DisplayName("여러 날짜도 날짜마다 그날 이전 가장 가까운 값이라 빠진 날 · 주말은 앞 영업일 환율을 준다")
  void readsManyWithPreviousRate() {
    LocalDate wed = LocalDate.of(2026, 6, 24);
    LocalDate thu = LocalDate.of(2026, 6, 25);
    LocalDate fri = LocalDate.of(2026, 6, 26);
    LocalDate sat = LocalDate.of(2026, 6, 27);
    given(repository.findFirstByDateLessThanEqualOrderByDateDesc(wed)).willReturn(Optional.of(rate(wed, "1545.30")));
    given(repository.findByDateBetween(wed, sat)).willReturn(List.of(rate(wed, "1545.30"), rate(fri, "1544.20")));

    assertThat(service.findByDates(List.of(sat, thu, wed, fri))).containsOnly(
      entry(wed, new BigDecimal("1545.30")), entry(thu, new BigDecimal("1545.30")),
      entry(fri, new BigDecimal("1544.20")), entry(sat, new BigDecimal("1544.20")));
  }

  @Test
  @DisplayName("여러 날짜에서 그 전 환율이 아예 없는 날은 빼고, 날짜 목록이 비면 조회하지 않는다")
  void readsManyWithoutEarlierRate() {
    LocalDate before = LocalDate.of(2015, 12, 31);
    LocalDate first = LocalDate.of(2016, 1, 4);
    given(repository.findFirstByDateLessThanEqualOrderByDateDesc(before)).willReturn(Optional.empty());
    given(repository.findByDateBetween(before, first)).willReturn(List.of(rate(first, "1172.00")));

    assertThat(service.findByDates(List.of(before, first))).containsOnlyKeys(first);
    assertThat(service.findByDates(List.of())).isEmpty();
    assertThat(service.findByDates(null)).isEmpty();
    verify(repository).findByDateBetween(any(), any());
  }

  @Test
  @DisplayName("최신 환율은 DB 에 있으면 그 값, 없으면 비운다")
  void latest() {
    given(repository.findLatestRate()).willReturn(Optional.of(rate("1330")), Optional.empty());

    assertThat(service.getLatestRate()).isPresent();
    assertThat(service.getLatestRate()).isEmpty();
  }
}
