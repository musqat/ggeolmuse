package com.muscat.marketdata.provider.koreaExim;

import com.muscat.marketdata.provider.MarketDataProvider.FxSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 한국수출입은행 FX 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KoreaEximFxSource implements FxSource {

  private final FxDataProvider fxDataProvider;

  @Override
  public Optional<BigDecimal> fetchUsdKrw(LocalDate date) {
    return fxDataProvider.fetchUsdKrw(date);
  }
}