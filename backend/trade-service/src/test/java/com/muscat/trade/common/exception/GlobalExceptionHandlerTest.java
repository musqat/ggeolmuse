package com.muscat.trade.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.responses.TradeResponse;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

@DisplayName("trade 예외 핸들러")
class GlobalExceptionHandlerTest {

  // 스프링이 advice 마다 만드는 resolver. 같은 예외에 메서드가 둘 걸리면 생성에서 실패한다
  private final ExceptionHandlerMethodResolver resolver =
    new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/trading/buy");

  private static GlobalExceptionHandler handler(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    return new GlobalExceptionHandler(environment);
  }

  private String handlerFor(Exception e) {
    return Objects.requireNonNull(resolver.resolveMethod(e)).getName();
  }

  @SuppressWarnings("unused")
  private static void buy(Object body) {
  }

  @Test
  @DisplayName("서비스 예외는 BOM 에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedBusinessHandler() {
    assertThat(handlerFor(new TradeException(TradeResponse.INSUFFICIENT_BALANCE))).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new NotEnoughHoldingsException())).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new IllegalStateException("원인"))).isEqualTo("handleGeneralException");
  }

  @Test
  @DisplayName("운영에서는 예상하지 못한 예외의 내용을 응답에 싣지 않고, 개발 프로필에서만 보여 준다")
  void generalException_DetailByProfile() {
    ResponseEntity<ProblemDetail> prod = handler("prod")
      .handleGeneralException(new IllegalStateException("jdbc:postgresql://db/secret"), request);
    ResponseEntity<ProblemDetail> dev = handler("dev")
      .handleGeneralException(new IllegalStateException("원인"), request);

    assertThat(prod.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(prod.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("거래 서비스에 문제가 발생했습니다.");
    assertThat(dev.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("원인 - IllegalStateException");
  }

  @Test
  @DisplayName("필수 파라미터 누락과 요청 본문 검증 실패는 400 이다")
  void clientErrors_BadRequest() throws Exception {
    GlobalExceptionHandler handler = handler();
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "tradeRequestDto");
    binding.addError(new FieldError("tradeRequestDto", "quantity", "수량은 0보다 커야 합니다"));
    MethodParameter parameter = new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("buy", Object.class), 0);

    ResponseEntity<ProblemDetail> missing = handler.handleMissingParameterException(
      new MissingServletRequestParameterException("startDate", "LocalDate"), request);
    ResponseEntity<ProblemDetail> invalid = handler.handleValidationException(
      new MethodArgumentNotValidException(parameter, binding), request);

    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missing.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("필수 파라미터가 누락되었습니다: startDate");
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invalid.getBody()).isNotNull();
  }
}
