package com.muscat.backtest.common.util;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.commonlib.dto.OHLCPriceDto;
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
   * 가격 계산용 유효 종가. 액면분할/배당 반영된 adjustedClose 우선, 없으면 closePrice fallback
   */
  public static java.math.BigDecimal effectiveClose(OHLCPriceDto price) {
    return price.adjustedClose() != null ? price.adjustedClose() : price.closePrice();
  }

  /**
   * fetcher로 maxDays 만큼 과거로 거슬러 available 데이터를 찾음(시장 휴일 대응)
   * 못 찾으면 STOCK_DATA_NOT_FOUND 예외 (BacktestDataUtils / SimImpl 경로와 동일)
   */
  public static OHLCPriceDto withFallback(PriceFetcher fetcher, String symbol, LocalDate date, int maxDays) {
    LocalDate searchDate = date;
    for (int i = 0; i < maxDays; i++) {
      try {
        OHLCPriceDto response = fetcher.get(symbol, searchDate.toString());
        if (response != null && response.available()) {
          return response;
        }
      } catch (Exception ignored) {
        // 조회 실패 → 하루 전으로 계속
      }
      searchDate = searchDate.minusDays(1);
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
