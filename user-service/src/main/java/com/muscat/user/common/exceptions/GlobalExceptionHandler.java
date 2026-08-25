package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.commonlib.util.ProblemDetailUtils;
import com.muscat.commonlib.enums.ErrorType;
import com.muscat.user.common.enums.responses.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = {
    com.muscat.user.domain.user.controller.AuthController.class,
    com.muscat.user.domain.user.controller.UserController.class,
    com.muscat.user.domain.account.controller.AccountController.class,
    com.muscat.user.domain.account.controller.AccountHistoryController.class
})
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

  private final Environment environment;

  public GlobalExceptionHandler(Environment environment) {
    this.environment = environment;
  }

  @ExceptionHandler(UserException.class)
  public ResponseEntity<ProblemDetail> handleUserException(UserException e, HttpServletRequest request) {
    log.warn("[USER ERROR] {}", e.getMessage());

    Map<String, Object> properties = Map.of("errorType", ErrorType.BUSINESS.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        e.getHttpStatus(),
        e.getMessage(),
        e.getErrorCode(),
        request.getRequestURI(),
        "User Error",
        properties
    );

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(SocialLoginException.class)
  public ResponseEntity<ProblemDetail> handleSocialLoginException(SocialLoginException e, HttpServletRequest request) {
    log.error("[SOCIAL LOGIN ERROR] {}", e.getMessage(), e);

    Map<String, Object> properties = Map.of("errorType", ErrorType.BUSINESS.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        e.getHttpStatus(),
        e.getMessage(),
        e.getErrorCode(),
        request.getRequestURI(),
        "Social Login Error",
        properties
    );

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(KeycloakException.class)
  public ResponseEntity<ProblemDetail> handleKeycloakException(KeycloakException e, HttpServletRequest request) {
    log.error("[KEYCLOAK ERROR] {}", e.getMessage(), e);

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getHttpStatus(), e.getMessage());
    problem.setType(URI.create("https://api.muscat.com/problems/keycloak-" + e.getErrorCode().toLowerCase().replace("_", "-")));
    problem.setTitle("Keycloak Error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", e.getErrorCode());
    problem.setProperty("errorType", ErrorType.UNAUTHORIZED.name());
    problem.setProperty("timestamp", LocalDateTime.now());

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException e, HttpServletRequest request) {
    log.warn("[AUTH ERROR] {}", e.getMessage());

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getHttpStatus(), e.getMessage());
    problem.setType(URI.create("https://api.muscat.com/problems/authentication-" + e.getErrorCode().toLowerCase().replace("_", "-")));
    problem.setTitle("Authentication Error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", e.getErrorCode());
    problem.setProperty("errorType", ErrorType.UNAUTHORIZED.name());
    problem.setProperty("timestamp", LocalDateTime.now());

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(AccountException.class)
  public ResponseEntity<ProblemDetail> handleAccountException(AccountException e, HttpServletRequest request) {
    log.warn("[ACCOUNT ERROR] {}", e.getMessage());

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getHttpStatus(), e.getMessage());
    problem.setType(URI.create("https://api.muscat.com/problems/account-" + e.getErrorCode().toLowerCase().replace("_", "-")));
    problem.setTitle("Account Error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", e.getErrorCode());
    problem.setProperty("errorType", ErrorType.BUSINESS);
    problem.setProperty("timestamp", LocalDateTime.now());

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(AccountHistoryException.class)
  public ResponseEntity<ProblemDetail> handleAccountHistoryException(AccountHistoryException e, HttpServletRequest request) {
    log.warn("[ACCOUNT HISTORY ERROR] {}", e.getMessage());

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getHttpStatus(), e.getMessage());
    problem.setType(URI.create("https://api.muscat.com/problems/account-history-" + e.getErrorCode().toLowerCase().replace("_", "-")));
    problem.setTitle("Account History Error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", e.getErrorCode());
    problem.setProperty("errorType", ErrorType.BUSINESS);
    problem.setProperty("timestamp", LocalDateTime.now());

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
    return super.handleValidationException(e, request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
    log.warn("[MISSING PARAMETER] {}", e.getParameterName());

    String message = String.format("필수 파라미터가 누락되었습니다: %s", e.getParameterName());

    Map<String, Object> properties = Map.of(
        "parameterName", e.getParameterName(),
        "errorType", ErrorType.VALIDATION.name()
    );
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        UserResponse.INVALID_INPUT.getHttpStatus(),
        message,
        "MISSING_PARAMETER",
        request.getRequestURI(),
        "Missing Parameter Error",
        properties
    );

    return ResponseEntity.badRequest().body(problem);
  }

  // 숫자 path 변수에 문자가 들어오는 등 타입 변환 실패.
  // 클라이언트 잘못이므로 400. 예외 클래스명·입력값을 detail에 싣지 않는다.
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    log.warn("[TYPE MISMATCH] parameter={}", e.getName());

    Map<String, Object> properties = Map.of("errorType", ErrorType.VALIDATION.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        UserResponse.INVALID_INPUT.getHttpStatus(),
        "요청 파라미터 형식이 올바르지 않습니다",
        "INVALID_PARAMETER",
        request.getRequestURI(),
        "Invalid Parameter",
        properties
    );

    return ResponseEntity.badRequest().body(problem);
  }

  // 깨진 JSON 본문. 마찬가지로 클라이언트 잘못이라 400, 파싱 예외 내용은 감춘다.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    log.warn("[MALFORMED BODY] {}", e.getMostSpecificCause().getClass().getSimpleName());

    Map<String, Object> properties = Map.of("errorType", ErrorType.VALIDATION.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        UserResponse.INVALID_INPUT.getHttpStatus(),
        "요청 본문을 읽을 수 없습니다",
        "MALFORMED_REQUEST_BODY",
        request.getRequestURI(),
        "Malformed Request Body",
        properties
    );

    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception e, HttpServletRequest request) {
    log.error("[SYSTEM ERROR] 예상치 못한 오류 발생", e);

    // 개발 환경에서는 상세한 에러 메시지 제공
    String message = isDevelopmentEnvironment() 
        ? String.format("%s - %s", e.getMessage(), e.getClass().getSimpleName())
        : "서버에 문제가 발생했습니다. 관리자에게 문의해주세요.";

    Map<String, Object> properties = Map.of("errorType", ErrorType.SYSTEM.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        UserResponse.INTERNAL_SERVER_ERROR.getHttpStatus(),
        message,
        "INTERNAL_SERVER_ERROR",
        request.getRequestURI(),
        "Internal Server Error",
        properties
    );

    return ResponseEntity.status(UserResponse.INTERNAL_SERVER_ERROR.getHttpStatus()).body(problem);
  }
  
  private boolean isDevelopmentEnvironment() {
    for (String profile : environment.getActiveProfiles()) {
      if (profile.equals("dev") || profile.equals("development") || profile.equals("local")) {
        return true;
      }
    }
    return false;
  }
}
