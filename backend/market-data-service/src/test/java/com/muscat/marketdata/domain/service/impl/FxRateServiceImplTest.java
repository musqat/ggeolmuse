package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
    return FxRate.builder().date(DATE).rate(new BigDecimal(value)).build();
  }

  @Test
  @DisplayName("날짜 조회는 그날 이전 가장 가까운 영업일 값, 날짜 목록이 비면 조회하지 않는다")
  void reads() {
    given(repository.findFirstByDateLessThanEqualOrderByDateDesc(DATE)).willReturn(Optional.of(rate("1330")), Optional.empty());
    given(repository.findByDateIn(List.of(DATE))).willReturn(List.of(rate("1330")));

    assertThat(service.findByDate(DATE).getRate()).isEqualByComparingTo("1330");
    assertThat(service.findByDate(DATE)).isNull();
    assertThat(service.findByDates(List.of(DATE))).hasSize(1);
    assertThat(service.findByDates(List.of())).isEmpty();
    assertThat(service.findByDates(null)).isEmpty();
    verify(repository).findByDateIn(anyList());
  }

  @Test
  @DisplayName("최신 환율은 DB 에 있으면 그 값, 없으면 비운다")
  void latest() {
    given(repository.findLatestRate()).willReturn(Optional.of(rate("1330")), Optional.empty());

    assertThat(service.getLatestRate()).isPresent();
    assertThat(service.getLatestRate()).isEmpty();
  }
}
