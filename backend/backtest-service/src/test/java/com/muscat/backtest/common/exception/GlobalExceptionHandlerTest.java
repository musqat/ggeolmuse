package com.muscat.backtest.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.api.controller.BacktestAnalysisController;
import com.muscat.backtest.api.controller.BacktestHistoryController;
import com.muscat.backtest.api.controller.TradingSimulationController;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

@DisplayName("backtest 예외 핸들러")
class GlobalExceptionHandlerTest {

  private static final String HIDDEN = "백테스트 서비스에 문제가 발생했습니다. 관리자에게 문의해주세요.";

  // 스프링이 advice 마다 만드는 resolver(같은 예외에 메서드가 둘 걸리면 생성에서 실패)
  private final ExceptionHandlerMethodResolver resolver =
    new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analysis/strategy/dca");

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

  private static String detail(ResponseEntity<ProblemDetail> response) {
    return Objects.requireNonNull(response.getBody()).getDetail();
  }

  @SuppressWarnings("unused")
  private static void dca(Object body) {
  }

  @Test
  @DisplayName("서비스 예외 · 요청 값 오류는 공통 모듈에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedHandlers() throws Exception {
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "dcaStrategyRequest");
    binding.addError(new FieldError("dcaStrategyRequest", "symbol", "종목 코드는 필수입니다"));
    MethodParameter parameter = new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("dca", Object.class), 0);

    assertThat(handlerFor(new BacktestException(BacktestResponse.INVALID_REQUEST))).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new MissingServletRequestParameterException("userId", "String")))
      .isEqualTo("handleMissingParameterException");
    assertThat(handlerFor(new MethodArgumentNotValidException(parameter, binding))).isEqualTo("handleValidationException");
    assertThat(handlerFor(new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]))))
      .isEqualTo("handleNotReadable");
    assertThat(handlerFor(new IllegalStateException("원인"))).isEqualTo("handleGeneralException");
  }

  @Test
  @DisplayName("운영이나 프로필이 없으면 예외 내용을 싣지 않는다")
  void generalException_ProdHidesDetail() {
    IllegalStateException leak = new IllegalStateException("jdbc:postgresql://db/secret");

    assertThat(detail(handler("prod").handleGeneralException(leak, request))).isEqualTo(HIDDEN);
    assertThat(detail(handler().handleGeneralException(leak, request))).isEqualTo(HIDDEN);
    assertThat(detail(handler("test", "docker").handleGeneralException(leak, request))).isEqualTo(HIDDEN);
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

    assertThat(List.of(BacktestAnalysisController.class, BacktestHistoryController.class,
      TradingSimulationController.class, TestPackageController.class))
      .allMatch(advice::isApplicableToBeanType);
    assertThat(advice.isApplicableToBeanType(BasicErrorController.class)).isFalse();
  }
}
