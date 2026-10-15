package com.muscat.backtest.common.util;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.commonlib.dto.OHLCPriceDto;
import feign.FeignException;
import java.time.LocalDate;
import java.util.Map;

/**
 * 주가 조회 + 시장 휴일(최대 N일) fallback 검색 단일화
 */
public final class PriceLookup {

  /** 가격 fetcher: (symbol, dateStr) → OHLCPriceDto. client/wrapper의 getOHLCPrice 메서드 참조로 충족 */
  @FunctionalInterface
  public interface PriceFetcher {
    OHLCPriceDto get(String symbol, String dateStr);
  }

  private PriceLookup() {}

  /**
   * 가격 계산용 유효 종가. 분할만 조정된 closePrice 우선. 배당은 따로 재투자한다
   */
  public static java.math.BigDecimal effectiveClose(OHLCPriceDto price) {
    return price.closePrice() != null ? price.closePrice() : price.adjustedClose();
  }

  /**
   * fetcher로 maxDays 만큼 과거로 거슬러 available 데이터를 찾음(시장 휴일 대응)
   * 못 찾으면 STOCK_DATA_NOT_FOUND. 한 번도 답을 못 받았으면 MARKET_DATA_UNAVAILABLE
   */
  public static OHLCPriceDto withFallback(PriceFetcher fetcher, String symbol, LocalDate date, int maxDays) {
    LocalDate searchDate = date;
    boolean answered = false;
    Exception lastError = null;
    for (int i = 0; i < maxDays; i++) {
      try {
        OHLCPriceDto response = fetcher.get(symbol, searchDate.toString());
        answered = true;
        if (response != null && response.available()) {
          return response;
        }
      } catch (FeignException.FeignClientException e) {
        // 4xx 는 시세 서비스가 답한 것. 그날 쓸 데이터가 없다
        answered = true;
      } catch (Exception e) {
        // 조회 실패 → 하루 전으로 계속
        lastError = e;
      }
      searchDate = searchDate.minusDays(1);
    }
    if (!answered && lastError != null) {
      throw new BacktestException(BacktestResponse.MARKET_DATA_UNAVAILABLE, lastError);
    }
    throw new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND,
        "주가 데이터를 찾을 수 없습니다 (" + maxDays + "일 검색): " + symbol + ", " + date);
  }

  /**
   * 인메모리 priceMap에서 maxDays 만큼 과거로 거슬러 첫 non-null을 반환
   */
  public static OHLCPriceDto fromMap(Map<LocalDate, OHLCPriceDto> priceMap, LocalDate date, int maxDays) {
    LocalDate searchDate = date;
    for (int i = 0; i < maxDays; i++) {
      OHLCPriceDto p = priceMap.get(searchDate);
      if (p != null) {
        return p;
      }
      searchDate = searchDate.minusDays(1);
    }
    return null;
  }
}
