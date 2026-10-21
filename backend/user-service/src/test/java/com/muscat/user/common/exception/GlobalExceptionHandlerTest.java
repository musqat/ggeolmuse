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
    return new GlobalExceptionHandler(environment);
  }

  private String handlerFor(Exception e) {
    return Objects.requireNonNull(resolver.resolveMethod(e)).getName();
  }

  @SuppressWarnings("unused")
  private static void accountPath(Long accountId) {
  }

  @Test
  @DisplayName("서비스 예외는 BOM 에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedBusinessHandler() {
    assertThat(handlerFor(new UserException(UserResponse.USER_NOT_FOUND))).isEqualTo("handleBusinessException");
    assertThat(handlerFor(new AuthenticationException(UserResponse.AUTHENTICATION_FAILED)))
      .isEqualTo("handleBusinessException");
    assertThat(handlerFor(new ServiceException("EXCESSIVE_AMOUNT", "한도 초과"))).isEqualTo("handleServiceException");
    assertThat(handlerFor(new IllegalStateException("원인"))).isEqualTo("handleGenericException");
  }

  @Test
  @DisplayName("운영에서는 예상하지 못한 예외의 내용을 응답에 싣지 않는다")
  void generic_Production_HidesDetail() {
    ResponseEntity<ProblemDetail> response = handler("prod")
      .handleGenericException(new IllegalStateException("jdbc:postgresql://db/secret"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("서버에 문제가 발생했습니다.");
  }

  @Test
  @DisplayName("개발 프로필에서는 예외 메시지와 종류를 보여 준다")
  void generic_Development_ShowsDetail() {
    ResponseEntity<ProblemDetail> response = handler("dev")
      .handleGenericException(new IllegalStateException("원인"), request);

    assertThat(response.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("원인 - IllegalStateException");
  }

  @Test
  @DisplayName("필수 파라미터 누락 · 형식 오류 · 깨진 본문은 400 이고 파싱 예외 내용은 감춘다")
  void clientErrors_BadRequest() throws Exception {
    GlobalExceptionHandler handler = handler();
    MethodParameter accountId = new MethodParameter(
      GlobalExceptionHandlerTest.class.getDeclaredMethod("accountPath", Long.class), 0);

    ResponseEntity<ProblemDetail> missing = handler.handleMissingParameterException(
      new MissingServletRequestParameterException("rate", "BigDecimal"), request);
    ResponseEntity<ProblemDetail> mismatch = handler.handleTypeMismatch(
      new MethodArgumentTypeMismatchException("abc", Long.class, "accountId", accountId,
        new NumberFormatException("For input string: \"abc\"")), request);
    ResponseEntity<ProblemDetail> malformed = handler.handleNotReadable(
      new HttpMessageNotReadableException("JSON parse error: Unexpected character",
        new IllegalArgumentException("Unexpected character"), new MockHttpInputMessage(new byte[0])), request);

    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missing.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("필수 파라미터가 누락되었습니다: rate");
    assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mismatch.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("요청 파라미터 형식이 올바르지 않습니다");
    assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(malformed.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("요청 본문을 읽을 수 없습니다");
  }
}
