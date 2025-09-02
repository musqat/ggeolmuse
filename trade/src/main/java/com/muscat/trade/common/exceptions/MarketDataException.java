package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.enums.BaseResponseEnum;

public class MarketDataException extends TradeException {
  
  public MarketDataException(String symbol, String message) {
    super(BaseResponseEnum.MARKET_DATA_SERVICE_ERROR, "시장 데이터 조회 실패 [" + symbol + "]: " + message);
  }
  
  public MarketDataException(String symbol, String message, Throwable cause) {
    super(BaseResponseEnum.MARKET_DATA_SERVICE_ERROR, "시장 데이터 조회 실패 [" + symbol + "]: " + message);
  }
  
  public MarketDataException() {
    super(BaseResponseEnum.MARKET_DATA_SERVICE_ERROR);
  }
}