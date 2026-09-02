package com.muscat.trade.common.exception;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.trade.common.enums.responses.TradeResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TradeException extends BaseException {

  private final HttpStatus httpStatus;

  public TradeException(TradeResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }


  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}