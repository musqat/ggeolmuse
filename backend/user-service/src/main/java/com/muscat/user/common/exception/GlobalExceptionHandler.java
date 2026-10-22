package com.muscat.user.common.exception;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {
    com.muscat.user.domain.user.controller.AuthController.class,
    com.muscat.user.domain.user.controller.UserController.class,
    com.muscat.user.domain.account.controller.AccountController.class,
    com.muscat.user.api.controller.AdminUserController.class
})
public class GlobalExceptionHandler extends BaseExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
    return systemError(e, request, "서버에 문제가 발생했습니다.");
  }
}
