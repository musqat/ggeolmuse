package com.muscat.commonlib.exception;

import com.muscat.commonlib.enums.ErrorType;
import com.muscat.commonlib.util.ProblemDetailUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
public class BaseExceptionHandler {

  // 서비스마다 GlobalExceptionHandler 가 상속하는 공통 처리(서비스 예외를 같은 모양으로)
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
      ex.getCode(),
      request.getRequestURI(),
      status.getReasonPhrase(),
      Map.of("errorType", errorTypeOf(status).name())
    );
    return ResponseEntity.status(status).body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(
    MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult().getFieldErrors()
      .forEach(error -> validationErrors.put(error.getField(), error.getDefaultMessage()));
    ex.getBindingResult().getGlobalErrors()
      .forEach(error -> validationErrors.put(error.getObjectName(), error.getDefaultMessage()));

    // 로그에는 필드 이름만(거부된 값에 비밀번호가 섞일 수 있음)
    log.warn("[VALIDATION ERROR] {}", validationErrors.keySet());

    ProblemDetail problem = ProblemDetailUtils.createValidationProblem(request.getRequestURI(), validationErrors);
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingParameterException(
    MissingServletRequestParameterException ex, HttpServletRequest request) {
    log.warn("[MISSING PARAMETER] {}", ex.getParameterName());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      CommonResponse.MISSING_PARAMETER,
      request.getRequestURI(),
      "Missing Parameter Error",
      Map.of(
        "parameterName", ex.getParameterName(),
        "errorType", ErrorType.VALIDATION.name()
      )
    );
    return ResponseEntity.badRequest().body(problem);
  }

  // 컨트롤러 @Validated 의 경로 변수 · 쿼리 파라미터 검사 실패(걸린 파라미터 이름만, 입력값은 안 실음)
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
    ConstraintViolationException ex, HttpServletRequest request) {
    String parameters = ex.getConstraintViolations().stream()
      .map(BaseExceptionHandler::parameterName)
      .distinct()
      .sorted()
      .collect(Collectors.joining(", "));
    log.warn("[INVALID PARAMETER] {}", parameters);

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      CommonResponse.INVALID_PARAMETER,
      request.getRequestURI(),
      "Invalid Parameter Error",
      Map.of(
        "parameterName", parameters,
        "errorType", ErrorType.VALIDATION.name()
      )
    );
    return ResponseEntity.badRequest().body(problem);
  }

  // 위반 경로(메서드.파라미터[.요소])에서 꺼낸 파라미터 이름
  private static String parameterName(ConstraintViolation<?> violation) {
    String name = violation.getPropertyPath().toString();
    for (Path.Node node : violation.getPropertyPath()) {
      if (node.getKind() == ElementKind.PARAMETER) {
        name = node.getName();
      }
    }
    return name;
  }

  // 경로 변수 · 쿼리 파라미터 타입 변환 실패(파라미터 이름은 parameterName 에, 입력값은 안 실음)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(
    MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn("[TYPE MISMATCH] parameter={}", ex.getName());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      CommonResponse.INVALID_PARAMETER,
      request.getRequestURI(),
      "Invalid Parameter",
      Map.of(
        "parameterName", ex.getName(),
        "errorType", ErrorType.VALIDATION.name()
      )
    );
    return ResponseEntity.badRequest().body(problem);
  }

  // 엔티티에 없는 정렬 이름(받은 이름은 응답에 안 실음)
  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<ProblemDetail> handleSortProperty(
    PropertyReferenceException ex, HttpServletRequest request) {
    log.warn("[SORT PROPERTY] path={}", request.getRequestURI());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      CommonResponse.INVALID_PARAMETER,
      request.getRequestURI(),
      "Invalid Parameter",
      Map.of(
        "parameterName", "sort",
        "errorType", ErrorType.VALIDATION.name()
      )
    );
    return ResponseEntity.badRequest().body(problem);
  }

  // 깨진 JSON 본문(파싱 오류 내용은 detail 에 안 실음)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleNotReadable(
    HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("[MALFORMED BODY] {}", ex.getMostSpecificCause().getClass().getSimpleName());

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      CommonResponse.MALFORMED_REQUEST_BODY,
      request.getRequestURI(),
      "Malformed Request Body",
      Map.of("errorType", ErrorType.VALIDATION.name())
    );
    return ResponseEntity.badRequest().body(problem);
  }

  // 예상하지 못한 예외의 500 응답(예외 내용은 로그에만)
  protected ResponseEntity<ProblemDetail> systemError(Exception ex, HttpServletRequest request) {
    log.error("[SYSTEM ERROR] 예상치 못한 오류 발생", ex);

    ProblemDetail problem = ProblemDetailUtils.createProblem(
      CommonResponse.INTERNAL_SERVER_ERROR,
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
}
