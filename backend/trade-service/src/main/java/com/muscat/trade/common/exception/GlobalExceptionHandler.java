package com.muscat.trade.common.exception;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.trade.TradeApplication;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 컨트롤러의 예외 응답
@RestControllerAdvice(basePackageClasses = TradeApplication.class)
public class GlobalExceptionHandler extends BaseExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
    return systemError(e, request);
  }
}
