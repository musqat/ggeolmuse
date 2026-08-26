package com.muscat.backtest.common.exception;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.commonlib.util.ProblemDetailUtils;
import com.muscat.commonlib.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {
    com.muscat.backtest.domain.controller.BacktestAnalysisController.class,
    com.muscat.backtest.domain.controller.TradingSimulationController.class
})
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

  private final Environment environment;

  public GlobalExceptionHandler(Environment environment) {
    this.environment = environment;
  }

  @ExceptionHandler(BacktestException.class)
  public ResponseEntity<ProblemDetail> handleBacktestException(BacktestException e, HttpServletRequest request) {
    log.warn("[BACKTEST ERROR] {} - {}", e.getErrorCode().getCode(), e.getMessage());

    Map<String, Object> properties = Map.of("errorType", ErrorType.BUSINESS.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        e.getErrorCode().name(),
        request.getRequestURI(),
        "Backtest Error",
        properties
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(problem);
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
        BacktestResponse.INVALID_REQUEST.getHttpStatus(),
        message,
        "MISSING_PARAMETER",
        request.getRequestURI(),
        "Missing Parameter Error",
        properties
    );

    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
    log.error("[SYSTEM ERROR] 예상치 못한 오류 발생", e);

    // 개발 환경에서는 상세한 에러 메시지 제공
    String message = isDevelopmentEnvironment() 
        ? String.format("%s - %s", e.getMessage(), e.getClass().getSimpleName())
        : "백테스트 서비스에 문제가 발생했습니다. 관리자에게 문의해주세요.";

    Map<String, Object> properties = Map.of("errorType", ErrorType.SYSTEM.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        BacktestResponse.INTERNAL_SERVER_ERROR.getHttpStatus(),
        message,
        "INTERNAL_SERVER_ERROR",
        request.getRequestURI(),
        "Internal Server Error",
        properties
    );

    return ResponseEntity.status(BacktestResponse.INTERNAL_SERVER_ERROR.getHttpStatus()).body(problem);
  }

  private boolean isDevelopmentEnvironment() {
    // 프로파일은 환경변수(SPRING_PROFILES_ACTIVE)로 주입되므로 System.getProperty 로는
    // 안 잡힌다. 예전 코드는 기본값 "dev" 로 떨어져 운영에서도 개발로 판정, 상세 오류를
    // 노출했다. Spring 이 실제로 활성화한 프로파일을 본다.
    for (String profile : environment.getActiveProfiles()) {
      if (profile.equals("dev") || profile.equals("development") || profile.equals("local")) {
        return true;
      }
    }
    return false;
  }
}
