package com.muscat.marketdata.common.exception;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.commonlib.util.ProblemDetailUtils;
import com.muscat.commonlib.enums.ErrorType;
import com.muscat.marketdata.MarketDataApplication;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 컨트롤러의 예외 응답
@RestControllerAdvice(basePackageClasses = MarketDataApplication.class)
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

  @ExceptionHandler(YahooFinanceException.class)
  public ResponseEntity<ProblemDetail> handleYahooFinanceException(YahooFinanceException e, HttpServletRequest request) {
    log.error("[YAHOO FINANCE ERROR] {}", e.getMessage(), e);

    Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
      MarketDataResponse.YAHOO_FINANCE_ERROR.getHttpStatus(),
      MarketDataResponse.YAHOO_FINANCE_ERROR.getMessage() + ": " + e.getMessage(),
      "YAHOO_FINANCE_ERROR",
      request.getRequestURI(),
      "Yahoo Finance Service Error",
      properties
    );

    return ResponseEntity.status(MarketDataResponse.YAHOO_FINANCE_ERROR.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(AlphaVantageException.class)
  public ResponseEntity<ProblemDetail> handleAlphaVantageException(AlphaVantageException e, HttpServletRequest request) {
    log.error("[ALPHA VANTAGE ERROR] {}", e.getMessage(), e);

    Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
      MarketDataResponse.ALPHA_VANTAGE_ERROR.getHttpStatus(),
      MarketDataResponse.ALPHA_VANTAGE_ERROR.getMessage() + ": " + e.getMessage(),
      "ALPHA_VANTAGE_ERROR",
      request.getRequestURI(),
      "Alpha Vantage Service Error",
      properties
    );

    return ResponseEntity.status(MarketDataResponse.ALPHA_VANTAGE_ERROR.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(FxRateException.class)
  public ResponseEntity<ProblemDetail> handleFxRateException(FxRateException e, HttpServletRequest request) {
    log.error("[FX RATE ERROR] {}", e.getMessage(), e);

    Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
    ProblemDetail problem = ProblemDetailUtils.createProblem(
      MarketDataResponse.KOREA_EXIM_ERROR.getHttpStatus(),
      MarketDataResponse.KOREA_EXIM_ERROR.getMessage() + ": " + e.getMessage(),
      "FX_RATE_ERROR",
      request.getRequestURI(),
      "FX Rate Service Error",
      properties
    );

    return ResponseEntity.status(MarketDataResponse.KOREA_EXIM_ERROR.getHttpStatus()).body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
    return systemError(e, request);
  }
}
