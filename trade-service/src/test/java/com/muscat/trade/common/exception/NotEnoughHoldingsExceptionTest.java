package com.muscat.trade.common.exception;

import com.muscat.trade.common.enums.responses.TradeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotEnoughHoldingsException 테스트")
class NotEnoughHoldingsExceptionTest {

    @Test
    @DisplayName("기본 생성자로 예외 생성")
    void createException_DefaultConstructor_Success() {
        // When
        NotEnoughHoldingsException exception = new NotEnoughHoldingsException();

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(TradeResponse.INSUFFICIENT_HOLDINGS.getCode());
        assertThat(exception.getErrorMessage()).contains("보유 수량이 부족");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("예외가 TradeException을 상속함")
    void exception_ExtendsTradeException() {
        // When
        NotEnoughHoldingsException exception = new NotEnoughHoldingsException();

        // Then
        assertThat(exception).isInstanceOf(TradeException.class);
    }
}
