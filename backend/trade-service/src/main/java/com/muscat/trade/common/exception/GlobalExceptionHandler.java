package com.muscat.trade.common.exception;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.trade.api.controller.PortfolioController;
import com.muscat.trade.api.controller.TradeApiController;
import com.muscat.trade.api.controller.TradingController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {
    TradingController.class,
    PortfolioController.class,
    TradeApiController.class
})
public class GlobalExceptionHandler extends BaseExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
    return systemError(e, request, "거래 서비스에 문제가 발생했습니다.");
  }
}
