package com.muscat.commonlib.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("BusinessException")
class BusinessExceptionTest {

  enum TestCode implements ErrorCode {
    ITEM_NOT_FOUND("항목을 찾을 수 없습니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus httpStatus;

    TestCode(String message, HttpStatus httpStatus) {
      this.message = message;
      this.httpStatus = httpStatus;
    }

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
      return httpStatus;
    }
  }

  @Test
  @DisplayName("errorCode 는 enum 이름이고 메시지와 상태는 코드에서 가져온다")
  void fromCode() {
    BusinessException e = new BusinessException(TestCode.ITEM_NOT_FOUND);

    assertThat(e.getCode()).isEqualTo(TestCode.ITEM_NOT_FOUND);
    assertThat(e.getErrorCode()).isEqualTo("ITEM_NOT_FOUND");
    assertThat(e.getMessage()).isEqualTo("항목을 찾을 수 없습니다");
    assertThat(e.getErrorMessage()).isEqualTo("항목을 찾을 수 없습니다");
    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("메시지를 따로 주면 그 메시지를 쓴다")
  void customMessage() {
    BusinessException e = new BusinessException(TestCode.ITEM_NOT_FOUND, "AAPL 항목이 없습니다");

    assertThat(e.getErrorCode()).isEqualTo("ITEM_NOT_FOUND");
    assertThat(e.getMessage()).isEqualTo("AAPL 항목이 없습니다");
  }

  @Test
  @DisplayName("원인 예외를 담는다")
  void cause() {
    IllegalStateException cause = new IllegalStateException("원인");
    BusinessException e = new BusinessException(TestCode.ITEM_NOT_FOUND, cause);

    assertThat(e.getCause()).isSameAs(cause);
    assertThat(e.getMessage()).isEqualTo("항목을 찾을 수 없습니다");
  }
}
