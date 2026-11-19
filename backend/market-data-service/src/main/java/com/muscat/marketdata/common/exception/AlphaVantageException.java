package com.muscat.marketdata.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;

/**
 * Alpha Vantage 호출이 실패했을 때의 예외
 */
public class AlphaVantageException extends BusinessException {

  public AlphaVantageException(MarketDataResponse response) {
    super(response);
  }

  public AlphaVantageException(MarketDataResponse response, Throwable cause) {
    super(response, cause);
  }
}
