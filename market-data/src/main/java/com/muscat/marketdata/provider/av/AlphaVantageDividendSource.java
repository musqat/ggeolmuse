package com.muscat.marketdata.provider.av;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.provider.MarketDataProvider.DividendSource;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class AlphaVantageDividendSource implements DividendSource {

  private final AlphaVantageClient client;

  @Override
  public List<DividendDto> fetchDividends(String symbol, LocalDate from, LocalDate to) {
    String raw = client.getDividends(symbol);
    return AlphaParser.parseDividends(raw, symbol, from, to);
  }
}
