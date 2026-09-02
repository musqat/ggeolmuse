package com.muscat.trade.common.exception;

import com.muscat.trade.common.enums.responses.TradeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TradeException 테스트")
class TradeExceptionTest {

    @Test
    @DisplayName("TradeResponse로 예외 생성 시 정상 동작")
    void createException_WithTradeResponse_Success() {
        // Given
        TradeResponse response = TradeResponse.INSUFFICIENT_BALANCE;

        // When
        TradeException exception = new TradeException(response);

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(response.getCode());
        assertThat(exception.getErrorMessage()).isEqualTo(response.getMessage());
        assertThat(exception.getHttpStatus()).isEqualTo(response.getHttpStatus());
    }

    @Test
    @DisplayName("잔액 부족 예외 생성")
    void createException_InsufficientBalance() {
        // When
        TradeException exception = new TradeException(TradeResponse.INSUFFICIENT_BALANCE);

        // Then
        assertThat(exception.getErrorCode()).isEqualTo("400");
        assertThat(exception.getErrorMessage()).contains("잔액이 부족");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("계좌 미발견 예외 생성")
    void createException_AccountNotFound() {
        // When
        TradeException exception = new TradeException(TradeResponse.ACCOUNT_NOT_FOUND);

        // Then
        assertThat(exception.getErrorCode()).isEqualTo("404");
        assertThat(exception.getErrorMessage()).contains("계좌를 찾을 수 없");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("보유량 부족 예외 생성")
    void createException_InsufficientHoldings() {
        // When
        TradeException exception = new TradeException(TradeResponse.INSUFFICIENT_HOLDINGS);

        // Then
        assertThat(exception.getErrorCode()).isEqualTo("400");
        assertThat(exception.getErrorMessage()).contains("보유 수량이 부족");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
