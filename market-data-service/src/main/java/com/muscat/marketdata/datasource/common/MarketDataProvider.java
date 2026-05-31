package com.muscat.marketdata.datasource.common;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * - yahoo: Yahoo Finance 스크래핑 (개발)
 * - KoreaExim : 한국 수출입 은행(개발)
 * - alphavantage, FX: AlphaVantage API (운영)
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
   * 심볼 소스 (전체 종목 리스트)
   */
  public interface SymbolSource {

    List<Asset> fetchSymbols();
  }

  /**
   * 단일 종목 상세 조회 소스 (미리보기/등록용)
   * AlphaVantage: OVERVIEW API 1콜로 전부. Yahoo: chart meta 사용.
   */
  public interface AssetInfoSource {
    Asset getAsset(String symbol);
  }

  /**
   * 시가총액 갱신 소스
   * AlphaVantage: OVERVIEW per-symbol. Yahoo: NASDAQ Screener bulk.
   */
  public interface MarketCapSource {
    int updateAllMarketCaps(List<Asset> assets);
    boolean updateMarketCap(String symbol);
  }

  /**
   * 환율 소스
   */
  public interface FxSource {

    Optional<BigDecimal> fetchFx(LocalDate date);
  }

}
