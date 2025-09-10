package com.muscat.marketdata.provider;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * MarketData Provider 인터페이스
 * - yahoo: Yahoo Finance 스크래핑 (개발용)
 * - alphavantage: AlphaVantage API (운영용)
 * - FX: KoreaExim API 환율
 */
public final class MarketDataProvider {

  private MarketDataProvider() {
  }

  /**
   * 일봉 소스
   */
  public interface CandleSource {

    List<Candle> fetchDailyAdjusted(String symbol, LocalDate from, LocalDate to);
  }

  /**
   * 배당 소스
   */
  public interface DividendSource {

    List<DividendDto> fetchDividends(String symbol, LocalDate from, LocalDate to);
  }

  /**
   * 심볼 소스
   */
  public interface SymbolSource {

    List<Asset> fetchSymbols();

    default List<String> fetchSymbolStrings() {
      return fetchSymbols().stream().map(Asset::getSymbol).toList();
    }
  }

  /**
   * 환율 소스
   */
  public interface FxSource {

    Optional<BigDecimal> fetchFx(LocalDate date);
  }

}