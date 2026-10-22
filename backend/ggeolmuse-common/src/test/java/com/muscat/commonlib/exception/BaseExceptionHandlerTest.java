package com.muscat.commonlib.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@DisplayName("BaseExceptionHandler 서비스 예외 응답")
@ExtendWith(OutputCaptureExtension.class)
class BaseExceptionHandlerTest {

  private final BaseExceptionHandler handler = new BaseExceptionHandler();

  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/things");

  private record TestCode(String name, String message, HttpStatus httpStatus) implements ErrorCode {

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
      return httpStatus;
    }
  }

  // 서비스 처리기처럼 그 밖의 예외를 운영 문구와 함께 넘기는 처리기
  static class CatchAllHandler extends BaseExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, HttpServletRequest request) {
      return systemError(ex, request, "서비스에 문제가 발생했습니다.");
    }
  }

  private static CatchAllHandler catchAll(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    CatchAllHandler handler = new CatchAllHandler();
    handler.setEnvironment(environment);
    return handler;
  }

  @SuppressWarnings("unused")
  private static void submit(Object body) {
  }

  private static MethodParameter bodyParameter() throws NoSuchMethodException {
    return new MethodParameter(BaseExceptionHandlerTest.class.getDeclaredMethod("submit", Object.class), 0);
  }

  private static MethodArgumentNotValidException invalid(ObjectError error) throws NoSuchMethodException {
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "requestDto");
    binding.addError(error);
    return new MethodArgumentNotValidException(bodyParameter(), binding);
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "400, BUSINESS",
    "401, UNAUTHORIZED",
    "403, FORBIDDEN",
    "404, NOT_FOUND",
    "409, CONFLICT",
    "422, BUSINESS",
    "429, RATE_LIMIT",
    "500, SYSTEM",
    "502, EXTERNAL_SERVICE",
    "503, EXTERNAL_SERVICE",
    "504, EXTERNAL_SERVICE"
  })
  @DisplayName("errorType 은 HTTP 상태로 정한다")
  void errorTypeByStatus(int status, String errorType) {
    ResponseEntity<ProblemDetail> response = handler.handleBusinessException(
      new BusinessException(new TestCode("SOME_ERROR", "메시지", HttpStatus.valueOf(status))),
      new MockHttpServletRequest());

    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties()).containsEntry("errorType", errorType);
  }

  @Test
  @DisplayName("errorCode 는 enum 이름, title 은 상태 이유 문구, type 은 이름을 소문자와 하이픈으로 바꾼 값이다")
  void problemShape() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/market/prices/AAPL");

    ResponseEntity<ProblemDetail> response = handler.handleBusinessException(
      new BusinessException(
        new TestCode("PRICE_DATA_NOT_FOUND", "가격 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND)),
      request);

    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(404);
    assertThat(body.getTitle()).isEqualTo("Not Found");
    assertThat(body.getDetail()).isEqualTo("가격 데이터를 찾을 수 없습니다");
    assertThat(body.getType()).hasToString("https://api.muscat.com/problems/price-data-not-found");
    assertThat(body.getInstance()).hasToString("/api/market/prices/AAPL");
    assertThat(body.getProperties()).containsEntry("errorCode", "PRICE_DATA_NOT_FOUND");
  }

  @Test
  @DisplayName("금액 검증 실패(ServiceException)는 400 과 그 메시지로 응답한다")
  void serviceException_BadRequestWithMessage() {
    ResponseEntity<ProblemDetail> response = handler.handleServiceException(
      new ServiceException("EXCESSIVE_AMOUNT", "1회 최대 환전 금액은 50000000원입니다"),
      new MockHttpServletRequest("POST", "/api/accounts/18/exchange"));

    ProblemDetail body = response.getBody();
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(body).isNotNull();
    assertThat(body.getDetail()).isEqualTo("1회 최대 환전 금액은 50000000원입니다");
    assertThat(body.getProperties()).containsEntry("errorCode", "EXCESSIVE_AMOUNT");
    assertThat(body.getProperties()).containsEntry("errorType", "BUSINESS");
  }

  @Test
  @DisplayName("요청 값 오류는 서비스의 500 처리기가 있어도 기본 처리기의 400 처리기가 받는다")
  void clientErrorsResolveBeforeCatchAll() throws Exception {
    ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(CatchAllHandler.class);
    MethodParameter parameter = bodyParameter();

    Map<Exception, String> expected = Map.of(
      new MissingServletRequestParameterException("startDate", "LocalDate"), "handleMissingParameterException",
      new MethodArgumentTypeMismatchException("abc", Long.class, "accountId", parameter, null), "handleTypeMismatch",
      new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])), "handleNotReadable",
      invalid(new FieldError("requestDto", "quantity", "수량은 0보다 커야 합니다")), "handleValidationException",
      new IllegalStateException("원인"), "handleGeneralException"
    );

    expected.forEach((exception, method) ->
      assertThat(Objects.requireNonNull(resolver.resolveMethod(exception)).getName())
        .as(exception.getClass().getSimpleName())
        .isEqualTo(method));
  }

  @Test
  @DisplayName("본문 검증 실패는 필드 오류와 객체 오류를 모두 validationErrors 에 담아 400 으로 응답한다")
  void validation_FieldAndGlobalErrors() throws Exception {
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "requestDto");
    binding.addError(new FieldError("requestDto", "quantity", "수량은 0보다 커야 합니다"));
    binding.addError(new ObjectError("requestDto", "시작일은 종료일보다 앞이어야 합니다"));

    ResponseEntity<ProblemDetail> response = handler.handleValidationException(
      new MethodArgumentNotValidException(bodyParameter(), binding), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties()).extractingByKey("validationErrors")
      .isEqualTo(Map.of(
        "quantity", "수량은 0보다 커야 합니다",
        "requestDto", "시작일은 종료일보다 앞이어야 합니다"));
  }

  @Test
  @DisplayName("본문 검증 실패 로그에는 필드 이름만 남고 거부된 입력값은 남지 않는다")
  void validation_LogsFieldNamesOnly(CapturedOutput output) throws Exception {
    FieldError rejected = new FieldError("requestDto", "newPassword", "plain-secret-1", false,
      null, null, "비밀번호 형식이 올바르지 않습니다");

    handler.handleValidationException(invalid(rejected), request);

    assertThat(output).contains("newPassword").doesNotContain("plain-secret-1");
  }

  @Test
  @DisplayName("필수 파라미터 누락은 400 과 빠진 파라미터 이름으로 응답한다")
  void missingParameter_BadRequest() {
    ResponseEntity<ProblemDetail> response = handler.handleMissingParameterException(
      new MissingServletRequestParameterException("startDate", "LocalDate"), request);

    ProblemDetail body = response.getBody();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(400);
    assertThat(body.getDetail()).isEqualTo("필수 파라미터가 누락되었습니다: startDate");
    assertThat(body.getTitle()).isEqualTo("Missing Parameter Error");
    assertThat(body.getProperties())
      .containsEntry("errorCode", "MISSING_PARAMETER")
      .containsEntry("parameterName", "startDate")
      .containsEntry("errorType", "VALIDATION");
  }

  @Test
  @DisplayName("파라미터 타입 변환 실패와 읽을 수 없는 본문은 400 이고 입력값 · 파싱 오류는 detail 에 싣지 않는다")
  void unreadableInput_BadRequestWithoutInput() throws Exception {
    ResponseEntity<ProblemDetail> mismatch = handler.handleTypeMismatch(
      new MethodArgumentTypeMismatchException("abc", Long.class, "accountId", bodyParameter(), null), request);
    ResponseEntity<ProblemDetail> unreadable = handler.handleNotReadable(
      new HttpMessageNotReadableException("JSON parse error: Unexpected character",
        new MockHttpInputMessage(new byte[0])), request);

    assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mismatch.getBody()).isNotNull();
    assertThat(mismatch.getBody().getDetail()).isEqualTo("요청 파라미터 형식이 올바르지 않습니다");
    assertThat(mismatch.getBody().getProperties()).containsEntry("errorCode", "INVALID_PARAMETER");
    assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(unreadable.getBody()).isNotNull();
    assertThat(unreadable.getBody().getDetail()).isEqualTo("요청 본문을 읽을 수 없습니다");
    assertThat(unreadable.getBody().getProperties()).containsEntry("errorCode", "MALFORMED_REQUEST_BODY");
  }

  @Test
  @DisplayName("예상하지 못한 예외는 500 이고 운영에서는 서비스가 넘긴 문구만 detail 에 싣는다")
  void systemError_ProductionMessage() {
    IllegalStateException leak = new IllegalStateException("jdbc:postgresql://db/secret");

    ResponseEntity<ProblemDetail> prod = catchAll("prod").handleGeneralException(leak, request);
    ResponseEntity<ProblemDetail> noEnvironment = new CatchAllHandler().handleGeneralException(leak, request);

    assertThat(prod.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(prod.getBody()).isNotNull();
    assertThat(prod.getBody().getDetail()).isEqualTo("서비스에 문제가 발생했습니다.");
    assertThat(prod.getBody().getTitle()).isEqualTo("Internal Server Error");
    assertThat(prod.getBody().getProperties())
      .containsEntry("errorCode", "INTERNAL_SERVER_ERROR")
      .containsEntry("errorType", "SYSTEM");
    assertThat(noEnvironment.getBody()).extracting(ProblemDetail::getDetail).isEqualTo("서비스에 문제가 발생했습니다.");
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "dev, 원인 - IllegalStateException",
    "local, 원인 - IllegalStateException",
    "'no-file-logging,development', 원인 - IllegalStateException",
    "'test,docker', 서비스에 문제가 발생했습니다."
  })
  @DisplayName("켜진 프로필 중 개발용이 있을 때만 500 detail 에 예외 메시지와 종류를 싣는다")
  void systemError_DetailByProfile(String profiles, String detail) {
    ResponseEntity<ProblemDetail> response = catchAll(profiles.split(","))
      .handleGeneralException(new IllegalStateException("원인"), request);

    assertThat(response.getBody()).extracting(ProblemDetail::getDetail).isEqualTo(detail);
  }
}
