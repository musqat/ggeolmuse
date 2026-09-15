package com.muscat.marketdata.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;

/**
 * MarketDataResponse 의 코드 · 메시지 · HTTP 상태를 담는 예외
 */
public class MarketDataException extends BusinessException {

    public MarketDataException(MarketDataResponse response) {
        super(response);
    }
}
