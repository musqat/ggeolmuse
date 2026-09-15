package com.muscat.marketdata.common.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

@DisplayName("market-data 예외 응답 변환")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("서비스 예외는 BOM 에서 상속한 핸들러가, 외부 API 예외와 그 밖의 예외는 서비스 핸들러가 받는다")
  void resolvesInheritedBusinessHandler() {
    // 스프링이 advice 마다 만드는 resolver. 같은 예외에 메서드가 둘 걸리면 생성에서 실패한다
    ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    assertThat(resolver.resolveMethod(new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND)).getName())
      .isEqualTo("handleBusinessException");
    assertThat(resolver.resolveMethod(new YahooFinanceException("원인")).getName())
      .isEqualTo("handleYahooFinanceException");
    assertThat(resolver.resolveMethod(new IllegalStateException("원인")).getName())
      .isEqualTo("handleGeneralException");
  }

  @Test
  @DisplayName("MarketDataException 은 상속한 BOM 핸들러가 enum 이름 errorCode 로 바꾼다")
  void handleMarketDataException_UsesEnumName() {
    ResponseEntity<ProblemDetail> response = handler.handleBusinessException(
      new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND), new MockHttpServletRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTitle()).isEqualTo("Not Found");
    assertThat(body.getDetail()).isEqualTo("가격 데이터를 찾을 수 없습니다");
    assertThat(body.getProperties())
      .containsEntry("errorCode", "PRICE_DATA_NOT_FOUND")
      .containsEntry("errorType", "NOT_FOUND");
  }
}
