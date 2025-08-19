package com.muscat.marketdata.provider.av;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.CandleId;
import com.muscat.marketdata.provider.MarketDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AlphaVantageCandleSource implements MarketDataProvider.CandleSource {

  private final AlphaVantageClient client;

  @Override
  public List<Candle> fetchDailyAdjusted(String symbol, LocalDate from, LocalDate to) {
    if (symbol == null || from == null || to == null) {
      throw new IllegalArgumentException("symbol/from/to must not be null");
    }
    boolean full = isLongRange(from, to);

    String raw;
    try {
      // Adjusted (유료일 수 있음)
      raw = client.getDailyAdjustedRaw(symbol, full);
    } catch (RuntimeException ex) {
      // 프리미엄 에러/노트면 무료 DAILY로 폴백
      String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
      if (msg.contains("premium endpoint") || msg.contains("\"note\"") || msg.contains(
          "\"information\"")) {
        raw = client.getDailyRaw(symbol, full);
      } else {
        throw ex;
      }
    }

    var dtos = AlphaParser.parseDailyAdjusted(raw, from, to);

    List<Candle> results = new ArrayList<>(dtos.size());
    for (var dto : dtos) {
      String sym = dto.getSymbol() != null ? dto.getSymbol() : symbol;

      Candle candle = Candle.builder()
          .id(new CandleId(sym, dto.getDate()))
          .open(dto.getOpen())
          .high(dto.getHigh())
          .low(dto.getLow())
          .close(dto.getClose())
          .adjustedClose(dto.getAdjustedClose() != null ? dto.getAdjustedClose() : dto.getClose())
          .volume(dto.getVolume())
          .dividendAmount(BigDecimal.ZERO)  // Alpha Vantage에서 추출해서 설정
          .splitCoefficient(BigDecimal.ONE) // Alpha Vantage에서 추출해서 설정
          .build();

      results.add(candle);
    }
    return results;
  }
  private boolean isLongRange(LocalDate from, LocalDate to) {
    try {
      Period p = Period.between(from, to);
      return p.getYears() >= 1 || p.getMonths() >= 4 || p.getDays() > 120;
    } catch (Exception ignored) {
      return true;
    }
  }
}
