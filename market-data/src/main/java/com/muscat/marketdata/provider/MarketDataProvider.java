package com.muscat.marketdata.provider;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Provider 공용 인터페이스
 * - dev 프로필: Yahoo Finance 스크래핑으로  Candle/Dividend 소스로 주입
 * - prod 프로필: Alpha Vantage API로 Candle/Dividend 소스로 주입
 * - FX: KoreaExim API로  환율 소스로 주입
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
   * 심볼 소스 (stooq 이외의 소스 활용시 )
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

    Optional<BigDecimal> fetchUsdKrw(LocalDate date);
  }

}