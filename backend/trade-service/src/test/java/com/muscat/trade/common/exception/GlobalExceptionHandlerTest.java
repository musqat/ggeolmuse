package com.muscat.trade.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.response.TradeResponse;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
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
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    handler.setEnvironment(environment);
    return handler;
  }

  private String handlerFor(Exception e) {
    return Objects.requireNonNull(resolver.resolveMethod(e)).getName();
  }

  @SuppressWarnings("unused")
  private static void buy(Object body) {
  }

  @Test
  @DisplayName("서비스 예외 · 요청 값 오류는 공통 모듈에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedHandlers() throws Exception {
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "tradeRequestDto");
    binding.addError(new FieldError("tradeRequestDto", "quantity", "수량은 0보다 커야 합니다"));
    MethodParameter parameter = new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("buy", Object.class), 0);

    assertThat(handlerFor(new TradeException(TradeResponse.INSUFFICIENT_BALANCE))).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new NotEnoughHoldingsException())).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new MissingServletRequestParameterException("startDate", "LocalDate")))
      .isEqualTo("handleMissingParameterException");
    assertThat(handlerFor(new MethodArgumentNotValidException(parameter, binding))).isEqualTo("handleValidationException");
    assertThat(handlerFor(new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]))))
      .isEqualTo("handleNotReadable");
    assertThat(handlerFor(new IllegalStateException("원인"))).isEqualTo("handleGeneralException");
  }

  @Test
  @DisplayName("운영에서는 예상하지 못한 예외의 내용을 응답에 싣지 않는다")
  void generalException_Production_HidesDetail() {
    ResponseEntity<ProblemDetail> prod = handler("prod")
      .handleGeneralException(new IllegalStateException("jdbc:postgresql://db/secret"), request);

    assertThat(prod.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(prod.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("거래 서비스에 문제가 발생했습니다.");
  }
}
