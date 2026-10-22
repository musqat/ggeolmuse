package com.muscat.commonlib.exception;

import com.muscat.commonlib.enums.ErrorType;
import com.muscat.commonlib.util.ProblemDetailUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
public class BaseExceptionHandler implements EnvironmentAware {

  private static final List<String> DEVELOPMENT_PROFILES = List.of("dev", "development", "local");

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  // 서비스마다 GlobalExceptionHandler 가 상속해 서비스 예외를 같은 모양으로 내보낸다
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex,
    HttpServletRequest request) {
    HttpStatus status = ex.getHttpStatus();
    if (status.is5xxServerError()) {
      log.error("[BUSINESS ERROR] {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
    } else {
      log.warn("[BUSINESS ERROR] {} - {}", ex.getErrorCode(), ex.getMessage());
    }

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      status,
      ex.getMessage(),
      ex.getErrorCode(),
      request.getRequestURI(),
      status.getReasonPhrase(),
      Map.of("errorType", errorTypeOf(status).name())
    );

    return ResponseEntity.status(status).body(problem);
  }

  // MoneyUtils 의 금액 · 통화 · 환율 검증 실패. 요청 값 문제라 400
  @ExceptionHandler(ServiceException.class)
  public ResponseEntity<ProblemDetail> handleServiceException(ServiceException ex,
    HttpServletRequest request) {
    log.warn("[VALIDATION ERROR] {} - {}", ex.getErrorCode(), ex.getMessage());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      HttpStatus.BAD_REQUEST,
      ex.getMessage(),
      ex.getErrorCode(),
      request.getRequestURI(),
      HttpStatus.BAD_REQUEST.getReasonPhrase(),
      Map.of("errorType", ErrorType.BUSINESS.name())
    );

    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(
    MethodArgumentNotValidException ex, HttpServletRequest request) {

    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult().getFieldErrors()
      .forEach(error -> validationErrors.put(error.getField(), error.getDefaultMessage()));
    ex.getBindingResult().getGlobalErrors()
      .forEach(error -> validationErrors.put(error.getObjectName(), error.getDefaultMessage()));

    // 로그에는 필드 이름만. 거부된 값에 비밀번호가 섞일 수 있음
    log.warn("[VALIDATION ERROR] {}", validationErrors.keySet());

    ProblemDetail problem = ProblemDetailUtils.createValidationProblem(
      "입력값 검증에 실패했습니다",
      request.getRequestURI(),
      validationErrors
    );

    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingParameterException(
    MissingServletRequestParameterException ex, HttpServletRequest request) {
    log.warn("[MISSING PARAMETER] {}", ex.getParameterName());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      HttpStatus.BAD_REQUEST,
      String.format("필수 파라미터가 누락되었습니다: %s", ex.getParameterName()),
      "MISSING_PARAMETER",
      request.getRequestURI(),
      "Missing Parameter Error",
      Map.of(
        "parameterName", ex.getParameterName(),
        "errorType", ErrorType.VALIDATION.name()
      )
    );

    return ResponseEntity.badRequest().body(problem);
  }

  // 경로 변수 · 쿼리 파라미터 타입 변환 실패. 입력값은 detail 에 안 실음
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(
    MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn("[TYPE MISMATCH] parameter={}", ex.getName());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      HttpStatus.BAD_REQUEST,
      "요청 파라미터 형식이 올바르지 않습니다",
      "INVALID_PARAMETER",
      request.getRequestURI(),
      "Invalid Parameter",
      Map.of("errorType", ErrorType.VALIDATION.name())
    );

    return ResponseEntity.badRequest().body(problem);
  }

  // 깨진 JSON 본문. 파싱 오류 내용은 detail 에 안 실음
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleNotReadable(
    HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("[MALFORMED BODY] {}", ex.getMostSpecificCause().getClass().getSimpleName());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      HttpStatus.BAD_REQUEST,
      "요청 본문을 읽을 수 없습니다",
      "MALFORMED_REQUEST_BODY",
      request.getRequestURI(),
      "Malformed Request Body",
      Map.of("errorType", ErrorType.VALIDATION.name())
    );

    return ResponseEntity.badRequest().body(problem);
  }

  // 예상 못 한 예외의 500 응답. 개발 프로필에서만 detail 에 예외 메시지를 실음
  protected ResponseEntity<ProblemDetail> systemError(Exception ex, HttpServletRequest request,
    String productionMessage) {
    log.error("[SYSTEM ERROR] 예상치 못한 오류 발생", ex);

    String message = isDevelopmentEnvironment()
      ? String.format("%s - %s", ex.getMessage(), ex.getClass().getSimpleName())
      : productionMessage;

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      HttpStatus.INTERNAL_SERVER_ERROR,
      message,
      "INTERNAL_SERVER_ERROR",
      request.getRequestURI(),
      "Internal Server Error",
      Map.of("errorType", ErrorType.SYSTEM.name())
    );

    return ResponseEntity.internalServerError().body(problem);
  }

  private static ErrorType errorTypeOf(HttpStatus status) {
    return switch (status) {
      case UNAUTHORIZED -> ErrorType.UNAUTHORIZED;
      case FORBIDDEN -> ErrorType.FORBIDDEN;
      case NOT_FOUND -> ErrorType.NOT_FOUND;
      case CONFLICT -> ErrorType.CONFLICT;
      case TOO_MANY_REQUESTS -> ErrorType.RATE_LIMIT;
      case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> ErrorType.EXTERNAL_SERVICE;
      default -> status.is5xxServerError() ? ErrorType.SYSTEM : ErrorType.BUSINESS;
    };
  }

  // 켜진 프로필 중 하나라도 개발용이면 true. 환경이 없으면 운영으로 봄
  private boolean isDevelopmentEnvironment() {
    return environment != null
      && Arrays.stream(environment.getActiveProfiles()).anyMatch(DEVELOPMENT_PROFILES::contains);
  }
}
