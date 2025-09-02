package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.enums.type.ErrorType;
import com.muscat.trade.common.responses.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e,
      WebRequest request) {
    log.error("비즈니스 예외 발생: errorCode={}, message={}", e.getErrorCode().getCode(), e.getMessage(),
        e);

    HttpStatus status = HttpStatus.resolve(e.getErrorCode().getCode());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    ErrorType errorType = ErrorType.BUSINESS; // 기본적으로 비즈니스 에러로 분류

    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        status,
        e.getMessage(),
        errorType
    );

    return ResponseEntity.status(status).body(errorResponse);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e,
      WebRequest request) {
    log.warn("입력값 검증 실패: {}", e.getMessage());

    StringBuilder errorMessage = new StringBuilder("입력값 검증 실패: ");
    e.getBindingResult().getAllErrors().forEach(error -> {
      String fieldName = ((FieldError) error).getField();
      String message = error.getDefaultMessage();
      errorMessage.append(String.format("[%s: %s] ", fieldName, message));
    });

    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        HttpStatus.BAD_REQUEST,
        errorMessage.toString().trim(),
        ErrorType.VALIDATION
    );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }


  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception e, WebRequest request) {
    log.error("예상치 못한 오류 발생", e);

    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 내부 오류가 발생했습니다",
        ErrorType.SYSTEM
    );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
  }

}