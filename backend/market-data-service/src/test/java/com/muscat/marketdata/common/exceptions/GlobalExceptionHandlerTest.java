package com.muscat.marketdata.common.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("market-data 예외 응답 변환")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("MarketDataException 은 응답 코드의 HTTP 상태 · 메시지 · errorCode 를 담은 ProblemDetail 로 바뀐다")
  void handleMarketDataException_UsesResponseCode() {
    ResponseEntity<ProblemDetail> response = handler.handleMarketDataException(
      new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND), new MockHttpServletRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(404);
    assertThat(body.getDetail()).isEqualTo("가격 데이터를 찾을 수 없습니다");
    assertThat(body.getProperties())
      .containsEntry("errorCode", "404")
      .containsEntry("errorType", "BUSINESS");
  }
}
