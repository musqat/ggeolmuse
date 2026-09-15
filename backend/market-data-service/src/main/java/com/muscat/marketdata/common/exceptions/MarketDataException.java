package com.muscat.marketdata.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * MarketDataResponse 의 코드 · 메시지 · HTTP 상태를 담는 예외
 */
@Getter
public class MarketDataException extends BaseException {

    private final HttpStatus httpStatus;

    public MarketDataException(MarketDataResponse response) {
        super(response.getCode(), response.getMessage());
        this.httpStatus = response.getHttpStatus();
    }
}
