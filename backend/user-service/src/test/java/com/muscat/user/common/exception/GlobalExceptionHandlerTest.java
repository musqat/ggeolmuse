package com.muscat.user.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.commonlib.exception.ServiceException;
import com.muscat.user.common.enums.response.UserResponse;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@DisplayName("user 예외 핸들러 선택")
class GlobalExceptionHandlerTest {

  // 스프링이 advice 마다 만드는 resolver. 같은 예외에 메서드가 둘 걸리면 생성에서 실패한다
  private final ExceptionHandlerMethodResolver resolver =
    new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts/abc");

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
  private static void accountPath(Long accountId) {
  }

  @Test
  @DisplayName("서비스 예외 · 요청 값 오류는 공통 모듈에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedHandlers() throws Exception {
    MethodParameter accountId = new MethodParameter(
      GlobalExceptionHandlerTest.class.getDeclaredMethod("accountPath", Long.class), 0);

    assertThat(handlerFor(new UserException(UserResponse.USER_NOT_FOUND))).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new AuthenticationException(UserResponse.AUTHENTICATION_FAILED)))
      .isEqualTo("handleBusinessException");
    assertThat(handlerFor(new ServiceException("EXCESSIVE_AMOUNT", "한도 초과"))).isEqualTo("handleServiceException");
    assertThat(handlerFor(new MissingServletRequestParameterException("rate", "BigDecimal")))
      .isEqualTo("handleMissingParameterException");
    assertThat(handlerFor(new MethodArgumentTypeMismatchException("abc", Long.class, "accountId", accountId, null)))
      .isEqualTo("handleTypeMismatch");
    assertThat(handlerFor(new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]))))
      .isEqualTo("handleNotReadable");
    assertThat(handlerFor(new IllegalStateException("원인"))).isEqualTo("handleGeneralException");
  }

  @Test
  @DisplayName("운영에서는 예상하지 못한 예외의 내용을 응답에 싣지 않는다")
  void general_Production_HidesDetail() {
    ResponseEntity<ProblemDetail> response = handler("prod")
      .handleGeneralException(new IllegalStateException("jdbc:postgresql://db/secret"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("서버에 문제가 발생했습니다.");
  }
}
