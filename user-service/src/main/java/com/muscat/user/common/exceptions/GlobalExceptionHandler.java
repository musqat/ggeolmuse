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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {
    com.muscat.user.domain.user.controller.AuthController.class,
    com.muscat.user.domain.user.controller.UserController.class,
    com.muscat.user.domain.account.controller.AccountController.class,
    com.muscat.user.domain.account.controller.AccountHistoryController.class
})
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

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
    // 개발 환경 판단 로직 (추후 @Value 등으로 개선 가능)
    String[] activeProfiles = {"dev", "development", "local"};
    String currentProfile = System.getProperty("spring.profiles.active", "dev");
    return java.util.Arrays.asList(activeProfiles).contains(currentProfile);
  }
}