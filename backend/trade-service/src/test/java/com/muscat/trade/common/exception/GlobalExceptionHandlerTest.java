package com.muscat.trade.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.api.controller.DividendAdminController;
import com.muscat.trade.api.controller.PortfolioController;
import com.muscat.trade.api.controller.TradeApiController;
import com.muscat.trade.api.controller.TradingController;
import com.muscat.trade.api.controller.TransactionHistoryController;
import com.muscat.trade.common.enums.response.TradeResponse;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.ControllerAdviceBean;

@DisplayName("trade 예외 핸들러")
class GlobalExceptionHandlerTest {

  // 스프링이 advice 마다 만드는 resolver(같은 예외에 메서드가 둘 걸리면 생성에서 실패)
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

  // 컨트롤러 패키지 밖에 둔 컨트롤러
  @RestController
  static class TestPackageController {
  }

  @Test
  @DisplayName("서비스 패키지 아래 모든 컨트롤러에 적용되고 스프링 기본 오류 컨트롤러에는 적용되지 않는다")
  void appliesToEveryServiceController() {
    ControllerAdviceBean advice;
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(GlobalExceptionHandler.class)) {
      advice = ControllerAdviceBean.findAnnotatedBeans(context).get(0);
    }

    assertThat(List.of(TradingController.class, PortfolioController.class, TradeApiController.class,
      TransactionHistoryController.class, DividendAdminController.class, TestPackageController.class))
      .allMatch(advice::isApplicableToBeanType);
    assertThat(advice.isApplicableToBeanType(BasicErrorController.class)).isFalse();
  }
}
