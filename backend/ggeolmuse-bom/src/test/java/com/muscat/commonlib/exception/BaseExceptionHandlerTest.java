package com.muscat.commonlib.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("BaseExceptionHandler 서비스 예외 응답")
class BaseExceptionHandlerTest {

  private final BaseExceptionHandler handler = new BaseExceptionHandler();

  private record TestCode(String name, String message, HttpStatus httpStatus) implements ErrorCode {

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
      return httpStatus;
    }
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "400, BUSINESS",
    "401, UNAUTHORIZED",
    "403, FORBIDDEN",
    "404, NOT_FOUND",
    "409, CONFLICT",
    "422, BUSINESS",
    "429, RATE_LIMIT",
    "500, SYSTEM",
    "502, EXTERNAL_SERVICE",
    "503, EXTERNAL_SERVICE",
    "504, EXTERNAL_SERVICE"
  })
  @DisplayName("errorType 은 HTTP 상태로 정한다")
  void errorTypeByStatus(int status, String errorType) {
    ResponseEntity<ProblemDetail> response = handler.handleBusinessException(
      new BusinessException(new TestCode("SOME_ERROR", "메시지", HttpStatus.valueOf(status))),
      new MockHttpServletRequest());

    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties()).containsEntry("errorType", errorType);
  }

  @Test
  @DisplayName("errorCode 는 enum 이름, title 은 상태 이유 문구, type 은 이름을 소문자와 하이픈으로 바꾼 값이다")
  void problemShape() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/market/prices/AAPL");

    ResponseEntity<ProblemDetail> response = handler.handleBusinessException(
      new BusinessException(
        new TestCode("PRICE_DATA_NOT_FOUND", "가격 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND)),
      request);

    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(404);
    assertThat(body.getTitle()).isEqualTo("Not Found");
    assertThat(body.getDetail()).isEqualTo("가격 데이터를 찾을 수 없습니다");
    assertThat(body.getType()).hasToString("https://api.muscat.com/problems/price-data-not-found");
    assertThat(body.getInstance()).hasToString("/api/market/prices/AAPL");
    assertThat(body.getProperties()).containsEntry("errorCode", "PRICE_DATA_NOT_FOUND");
  }
}
