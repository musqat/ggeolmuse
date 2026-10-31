package com.muscat.backtest.common.exception;

import com.muscat.backtest.BacktestApplication;
import com.muscat.commonlib.exception.BaseExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 컨트롤러의 예외 응답
@RestControllerAdvice(basePackageClasses = BacktestApplication.class)
public class GlobalExceptionHandler extends BaseExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
    return systemError(e, request, "백테스트 서비스에 문제가 발생했습니다. 관리자에게 문의해주세요.");
  }
}
