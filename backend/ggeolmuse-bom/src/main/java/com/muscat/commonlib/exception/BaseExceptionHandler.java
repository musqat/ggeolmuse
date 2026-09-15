package com.muscat.commonlib.exception;

import com.muscat.commonlib.enums.ErrorType;
import com.muscat.commonlib.util.ProblemDetailUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
public class BaseExceptionHandler {

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

  protected ResponseEntity<ProblemDetail> handleValidationException(
    MethodArgumentNotValidException ex, HttpServletRequest request) {

    log.error("Validation 오류 발생: {}", ex.getMessage());

    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach(error -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      validationErrors.put(fieldName, errorMessage);
    });

    ProblemDetail problem = ProblemDetailUtils.createValidationProblem(
      "입력값 검증에 실패했습니다",
      request.getRequestURI(),
      validationErrors
    );

    return ResponseEntity.badRequest().body(problem);
  }

  protected ResponseEntity<ProblemDetail> handleGeneralException(Exception ex,
    HttpServletRequest request) {
    log.error("예상치 못한 오류 발생: {}", ex.getMessage(), ex);

    ProblemDetail problem = ProblemDetailUtils.createInternalServerError(request.getRequestURI());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
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
}
