package com.muscat.trade.common.exception;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.commonlib.util.ProblemDetailUtils;
import com.muscat.commonlib.enums.ErrorType;
import com.muscat.trade.common.enums.responses.TradeResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {
    com.muscat.trade.domain.controller.TradingController.class,
    com.muscat.trade.domain.controller.PortfolioController.class,
    com.muscat.trade.domain.controller.TradeApiController.class
})
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

  @ExceptionHandler(TradeException.class)
  public ResponseEntity<ProblemDetail> handleTradeException(TradeException e, HttpServletRequest request) {
    log.warn("[TRADE ERROR] {} - {}", e.getErrorCode(), e.getMessage());

    Map<String, Object> properties = Map.of("errorType", ErrorType.BUSINESS.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        e.getHttpStatus(),
        e.getMessage(),
        e.getErrorCode(),
        request.getRequestURI(),
        "Trade Error",
        properties
    );

    return ResponseEntity.status(e.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(MarketDataException.class)
  public ResponseEntity<ProblemDetail> handleMarketDataException(MarketDataException e, HttpServletRequest request) {
    log.error("[MARKET DATA ERROR] {}", e.getMessage(), e);

    Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        TradeResponse.MARKET_DATA_SERVICE_ERROR.getHttpStatus(),
        TradeResponse.MARKET_DATA_SERVICE_ERROR.getMessage(),
        "MARKET_DATA_ERROR",
        request.getRequestURI(),
        "Market Data Error",
        properties
    );

    return ResponseEntity.status(TradeResponse.MARKET_DATA_SERVICE_ERROR.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(NotEnoughHoldingsException.class)
  public ResponseEntity<ProblemDetail> handleNotEnoughHoldingsException(NotEnoughHoldingsException e, HttpServletRequest request) {
    log.warn("[HOLDINGS ERROR] {}", e.getMessage());

    Map<String, Object> properties = Map.of("errorType", ErrorType.BUSINESS.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        TradeResponse.INSUFFICIENT_HOLDINGS.getHttpStatus(),
        TradeResponse.INSUFFICIENT_HOLDINGS.getMessage(),
        "INSUFFICIENT_HOLDINGS",
        request.getRequestURI(),
        "Insufficient Holdings Error",
        properties
    );

    return ResponseEntity.status(TradeResponse.INSUFFICIENT_HOLDINGS.getHttpStatus()).body(problem);
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
        TradeResponse.INVALID_TRADE_REQUEST.getHttpStatus(),
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
        : "거래 서비스에 문제가 발생했습니다. 관리자에게 문의해주세요.";

    Map<String, Object> properties = Map.of("errorType", ErrorType.SYSTEM.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        TradeResponse.INTERNAL_SERVER_ERROR.getHttpStatus(),
        message,
        "INTERNAL_SERVER_ERROR",
        request.getRequestURI(),
        "Internal Server Error",
        properties
    );

    return ResponseEntity.status(TradeResponse.INTERNAL_SERVER_ERROR.getHttpStatus()).body(problem);
  }

  private boolean isDevelopmentEnvironment() {
    // 개발 환경 판단 로직
    String[] activeProfiles = {"dev", "development", "local"};
    String currentProfile = System.getProperty("spring.profiles.active", "dev");
    return java.util.Arrays.asList(activeProfiles).contains(currentProfile);
  }
}