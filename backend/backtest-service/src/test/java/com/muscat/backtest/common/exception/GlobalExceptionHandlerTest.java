package com.muscat.backtest.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.enums.BacktestResponse;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@DisplayName("backtest 예외 핸들러")
class GlobalExceptionHandlerTest {

  private static final String HIDDEN = "백테스트 서비스에 문제가 발생했습니다. 관리자에게 문의해주세요.";

  // 스프링이 advice 마다 만드는 resolver. 같은 예외에 메서드가 둘 걸리면 생성에서 실패한다
  private final ExceptionHandlerMethodResolver resolver =
    new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analysis/strategy/dca");

  private static GlobalExceptionHandler handler(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    return new GlobalExceptionHandler(environment);
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
  @DisplayName("서비스 예외는 BOM 에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedBusinessHandler() {
    assertThat(handlerFor(new BacktestException(BacktestResponse.INVALID_REQUEST))).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new IllegalStateException("원인"))).isEqualTo("handleGeneralException");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"dev", "development", "local"})
  @DisplayName("개발 프로필에서는 예상하지 못한 예외의 내용을 보여 준다")
  void generalException_DevShowsDetail(String profile) {
    ResponseEntity<ProblemDetail> response = handler(profile).handleGeneralException(new IllegalStateException("원인"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(detail(response)).isEqualTo("원인 - IllegalStateException");
  }

  @Test
  @DisplayName("운영이나 프로필이 없으면 예외 내용을 싣지 않는다")
  void generalException_ProdHidesDetail() {
    IllegalStateException leak = new IllegalStateException("jdbc:postgresql://db/secret");

    assertThat(detail(handler("prod").handleGeneralException(leak, request))).isEqualTo(HIDDEN);
    assertThat(detail(handler().handleGeneralException(leak, request))).isEqualTo(HIDDEN);
    assertThat(detail(handler("test", "docker").handleGeneralException(leak, request))).isEqualTo(HIDDEN);
  }

  @Test
  @DisplayName("필수 파라미터 누락과 요청 본문 검증 실패는 400 이다")
  void clientErrors_BadRequest() throws Exception {
    GlobalExceptionHandler handler = handler();
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "dcaStrategyRequest");
    binding.addError(new FieldError("dcaStrategyRequest", "symbol", "종목 코드는 필수입니다"));
    MethodParameter parameter = new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("dca", Object.class), 0);

    ResponseEntity<ProblemDetail> missing = handler.handleMissingParameterException(
      new MissingServletRequestParameterException("userId", "String"), request);
    ResponseEntity<ProblemDetail> invalid = handler.handleValidationException(
      new MethodArgumentNotValidException(parameter, binding), request);

    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(detail(missing)).isEqualTo("필수 파라미터가 누락되었습니다: userId");
    assertThat(Objects.requireNonNull(missing.getBody()).getProperties()).containsEntry("parameterName", "userId");
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
