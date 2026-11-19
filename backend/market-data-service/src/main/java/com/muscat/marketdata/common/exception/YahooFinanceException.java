package com.muscat.marketdata.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;

/**
 * Yahoo Finance 응답을 쓸 수 없을 때의 예외
 */
public class YahooFinanceException extends BusinessException {

  public YahooFinanceException(MarketDataResponse response) {
    super(response);
  }

  public YahooFinanceException(MarketDataResponse response, Throwable cause) {
    super(response, cause);
  }
}
