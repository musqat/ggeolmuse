package com.muscat.marketdata.datasource.alphavantage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.datasource.alphavantage.collector.FxDataCollector;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.infra.kafka.FxRateEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("AlphaVantage 환율 서비스")
class AlphaVantageFxRateServiceTest {

  private static final LocalDate DATE = LocalDate.of(2024, 3, 4);

  private final FxRateRepository repository = mock(FxRateRepository.class);
  private final FxRateEventProducer producer = mock(FxRateEventProducer.class);
  private final AlphaVantageFxRateService service = new AlphaVantageFxRateService(repository, mock(FxDataCollector.class),
    new MarketDataProperties(), producer);

  private static FxRate rate(String value) {
    return FxRate.builder().date(DATE).rate(new BigDecimal(value)).build();
  }

  @Test
  @DisplayName("처음 들어온 날짜면 소수 6자리로 맞춰 저장하고 직전 환율 없이 발행한다")
  void savesNew() {
    given(repository.findByDate(DATE)).willReturn(Optional.empty());
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    FxRate saved = service.saveRate(DATE, new BigDecimal("1330.1234567"));

    assertThat(saved.getRate()).isEqualByComparingTo("1330.123457");
    verify(producer).publishFxRateUpdated(saved, null);
  }

  @Test
  @DisplayName("같은 날짜에 다른 값이 오면 덮어쓰고 직전 환율과 함께 발행한다")
  void updatesChanged() {
    given(repository.findByDate(DATE)).willReturn(Optional.of(rate("1320")));
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    FxRate saved = service.saveRate(DATE, new BigDecimal("1330"));

    ArgumentCaptor<BigDecimal> previous = ArgumentCaptor.forClass(BigDecimal.class);
    verify(producer).publishFxRateUpdated(org.mockito.ArgumentMatchers.eq(saved), previous.capture());
    assertThat(previous.getValue()).isEqualByComparingTo("1320");
  }

  @Test
  @DisplayName("같은 값이면 저장도 발행도 하지 않고 기존 값을 돌려준다")
  void keepsSame() {
    FxRate existing = rate("1330.000000");
    given(repository.findByDate(DATE)).willReturn(Optional.of(existing));

    assertThat(service.saveRate(DATE, new BigDecimal("1330"))).isSameAs(existing);
    verify(repository, never()).save(any());
    verify(producer, never()).publishFxRateUpdated(any(), any());
  }

  @Test
  @DisplayName("날짜 · 환율이 없거나 환율이 0 이하면 저장하지 않는다")
  void rejectsInvalid() {
    assertThatThrownBy(() -> service.saveRate(null, BigDecimal.ONE)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.saveRate(DATE, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.saveRate(DATE, BigDecimal.ZERO)).isInstanceOf(RuntimeException.class);
    verify(repository, never()).save(any());
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
