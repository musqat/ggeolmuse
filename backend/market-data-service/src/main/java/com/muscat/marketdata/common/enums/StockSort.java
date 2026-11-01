package com.muscat.marketdata.common.enums;

import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import com.muscat.marketdata.common.exception.MarketDataException;

// 종목 목록 정렬 기준(요청 값은 marketCap · dividendYield)
public enum StockSort {
  MARKET_CAP("marketCap"),
  DIVIDEND_YIELD("dividendYield");

  private final String param;

  StockSort(String param) {
    this.param = param;
  }

  public static StockSort from(String value) {
    for (StockSort sort : values()) {
      if (sort.param.equalsIgnoreCase(value)) {
        return sort;
      }
    }
    throw new MarketDataException(MarketDataResponse.INVALID_SORT);
  }
}
