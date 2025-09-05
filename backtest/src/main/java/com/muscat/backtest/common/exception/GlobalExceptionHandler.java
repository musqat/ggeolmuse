package com.muscat.backtest.common.exception;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import com.muscat.backtest.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(BacktestException.class)
  public ResponseEntity<ErrorResponse> handleBacktestException(
      BacktestException e, HttpServletRequest request) {

    log.warn("[BACKTEST ERROR] {} - {}", e.getErrorCode().getCode(), e.getMessage());

    HttpStatus status = HttpStatus.valueOf(e.getErrorCode().getCode());

    ErrorResponse errorResponse = ErrorResponse.of(
        request.getRequestURI(),
        status.value(),
        e.getMessage(),
        String.valueOf(e.getErrorCode().getCode())
    );

    return ResponseEntity.status(status).body(errorResponse);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneralException(
      Exception e, HttpServletRequest request) {

    log.error("[SYSTEM ERROR] {} - {}", request.getRequestURI(), e.getMessage(), e);

    ErrorResponse errorResponse = ErrorResponse.of(
        request.getRequestURI(),
        BacktestResponseCode.INTERNAL_ERROR.getCode(),
        BacktestResponseCode.INTERNAL_ERROR.getMessage(),
        String.valueOf(BacktestResponseCode.INTERNAL_ERROR.getCode())
    );

    return ResponseEntity.status(HttpStatus.valueOf(BacktestResponseCode.INTERNAL_ERROR.getCode()))
        .body(errorResponse);
  }
}