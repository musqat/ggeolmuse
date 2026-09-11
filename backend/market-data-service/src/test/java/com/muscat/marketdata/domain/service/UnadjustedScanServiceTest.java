package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.UnadjustedScanService.ScanMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("분할 종목 탐색")
class UnadjustedScanServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

  @Mock
  private CandleRepository candleRepository;

  private UnadjustedScanService service;

  @BeforeEach
  void setUp() {
    service = new UnadjustedScanService(candleRepository);
  }

  @Test
  @DisplayName("SPLITS: 분할 계수가 기록된 종목 목록을 그대로 담는다")
  void 분할_기록_위임() {
    given(candleRepository.findSymbolsWithSplits(FROM)).willReturn(List.of("AAPL", "NVDA"));

    service.scan(FROM, ScanMode.SPLITS);

    assertThat(service.getLast().getSymbols()).containsExactly("AAPL", "NVDA");
    assertThat(service.getLast().getMode()).isEqualTo(ScanMode.SPLITS);
    verify(candleRepository, never()).findSymbolsByRatioSpread(any());
  }

  @Test
  @DisplayName("RATIO: 비율 쿼리 결과를 그대로 담는다")
  void 비율_위임() {
    given(candleRepository.findSymbolsByRatioSpread(FROM)).willReturn(List.of("KO", "T"));

    service.scan(FROM, ScanMode.RATIO);

    assertThat(service.getLast().getSymbols()).containsExactly("KO", "T");
    assertThat(service.getLast().getMode()).isEqualTo(ScanMode.RATIO);
    verify(candleRepository, never()).findSymbolsWithSplits(any());
  }
}
