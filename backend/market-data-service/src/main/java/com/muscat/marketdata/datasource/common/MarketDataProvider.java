package com.muscat.marketdata.datasource.common;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 데이터 공급원 이름과 공급원별 소스 인터페이스
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

  // 종목 하나의 상세(미리보기 · 등록). AlphaVantage 는 OVERVIEW, Yahoo 는 chart meta
  public interface AssetInfoSource {
    Asset getAsset(String symbol);
  }

  // 시가총액. AlphaVantage 는 종목마다 OVERVIEW, Yahoo 는 NASDAQ Screener 로 한꺼번에
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
