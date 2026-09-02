package com.muscat.trade.common.exception;

import com.muscat.trade.common.enums.responses.TradeResponse;

public class MarketDataException extends TradeException {

  public MarketDataException() {
    super(TradeResponse.MARKET_DATA_SERVICE_ERROR);
  }
  
}