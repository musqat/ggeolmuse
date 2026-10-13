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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.bind.MissingServletRequestParameterException;

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

  // 컨트롤러와 같은 모양의 파라미터 검사. @Validated 가 이 검사로 ConstraintViolationException 을 만든다
  @SuppressWarnings("unused")
  static class SampleController {
    void price(@Pattern(regexp = "^[A-Z]{1,16}$") String symbol, @Positive BigDecimal minAmount) {
    }
  }

  private static ConstraintViolationException violations(Object... args) throws NoSuchMethodException {
    Method method = SampleController.class.getDeclaredMethod("price", String.class, BigDecimal.class);
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Set<ConstraintViolation<SampleController>> found = factory.getValidator().forExecutables()
        .validateParameters(new SampleController(), method, args);
      return new ConstraintViolationException(found);
    }
  }

  @Test
  @DisplayName("경로 변수 · 쿼리 파라미터 검증 실패는 400 이고 걸린 파라미터 이름을 싣는다")
  void constraintViolation_BadRequest() throws Exception {
    assertThat(new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class).resolveMethod(violations("aapl", BigDecimal.ONE)))
      .extracting(Method::getName).isEqualTo("handleConstraintViolationException");

    ResponseEntity<ProblemDetail> response = handler.handleConstraintViolationException(
      violations("aapl", new BigDecimal("-1")), new MockHttpServletRequest("GET", "/api/market/price/aapl"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ProblemDetail body = Objects.requireNonNull(response.getBody());
    assertThat(body.getStatus()).isEqualTo(400);
    assertThat(body.getDetail()).isEqualTo("요청 값이 올바르지 않습니다: minAmount, symbol");
    assertThat(body.getProperties()).containsEntry("parameterName", "minAmount, symbol").containsEntry("errorType", "VALIDATION");
  }

  @Test
  @DisplayName("필수 파라미터 누락은 HTTP 상태와 본문 status 가 둘 다 400 이다")
  void missingParameter_StatusMatches() {
    ResponseEntity<ProblemDetail> response = handler.handleMissingParameterException(
      new MissingServletRequestParameterException("date", "LocalDate"), new MockHttpServletRequest("GET", "/api/market/ohlc/AAPL"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(Objects.requireNonNull(response.getBody()).getStatus()).isEqualTo(400);
  }
}
