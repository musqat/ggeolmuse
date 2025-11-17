package com.muscat.trade.common.exception;

import com.muscat.trade.common.enums.responses.TradeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarketDataException 테스트")
class MarketDataExceptionTest {

    @Test
    @DisplayName("기본 생성자로 예외 생성")
    void createException_DefaultConstructor_Success() {
        // When
        MarketDataException exception = new MarketDataException();

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(TradeResponse.MARKET_DATA_SERVICE_ERROR.getCode());
        assertThat(exception.getErrorMessage()).contains("데이터");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("예외가 TradeException을 상속함")
    void exception_ExtendsTradeException() {
        // When
        MarketDataException exception = new MarketDataException();

        // Then
        assertThat(exception).isInstanceOf(TradeException.class);
    }
}
