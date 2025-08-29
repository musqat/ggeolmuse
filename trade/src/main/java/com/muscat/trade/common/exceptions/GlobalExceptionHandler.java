package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.responses.ErrorResponse;
import com.muscat.trade.common.enums.type.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, WebRequest request) {
    log.error("비즈니스 예외 발생: errorCode={}, message={}", e.getErrorCode().getCode(), e.getMessage(), e);
    
    HttpStatus status = e.getErrorCode().getHttpStatus();
    ErrorType errorType = mapToErrorType(e.getErrorCode());
    
    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""), 
        status, 
        e.getMessage(),
        errorType
    );
    
    return ResponseEntity.status(status).body(errorResponse);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, WebRequest request) {
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

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> handleBindException(BindException e, WebRequest request) {
    log.warn("바인딩 오류: {}", e.getMessage());
    
    StringBuilder errorMessage = new StringBuilder("바인딩 오류: ");
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

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, WebRequest request) {
    log.warn("파라미터 타입 오류: parameter={}, value={}, requiredType={}", 
        e.getName(), e.getValue(), e.getRequiredType().getSimpleName());
    
    String message = String.format("파라미터 '%s'의 값 '%s'이(가) 올바르지 않습니다", 
        e.getName(), e.getValue());
    
    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        HttpStatus.BAD_REQUEST,
        message,
        ErrorType.VALIDATION
    );
    
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, WebRequest request) {
    log.warn("접근 거부: {}", e.getMessage());
    
    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        HttpStatus.FORBIDDEN,
        "접근 권한이 없습니다",
        ErrorType.FORBIDDEN
    );
    
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
    log.warn("잘못된 인수: {}", e.getMessage());
    
    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        HttpStatus.BAD_REQUEST,
        e.getMessage(),
        ErrorType.VALIDATION
    );
    
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e, WebRequest request) {
    log.error("잘못된 상태: {}", e.getMessage(), e);
    
    ErrorResponse errorResponse = ErrorResponse.of(
        request.getDescription(false).replace("uri=", ""),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 오류가 발생했습니다",
        ErrorType.SYSTEM
    );
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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

  // BaseResponseEnum을 ErrorType으로 매핑
  private ErrorType mapToErrorType(com.muscat.trade.common.enums.BaseResponseEnum errorCode) {
    String code = errorCode.getCode();
    return switch (code) {
      case "400" -> ErrorType.VALIDATION;
      case "404" -> ErrorType.NOT_FOUND;
      case "409" -> ErrorType.CONFLICT;
      case "403" -> ErrorType.FORBIDDEN;
      case "503" -> ErrorType.MARKET_DATA;
      default -> {
        if (errorCode.getMessage().contains("잔액") || errorCode.getMessage().contains("수량") || 
            errorCode.getMessage().contains("거래") || errorCode.getMessage().contains("보유")) {
          yield ErrorType.BUSINESS;
        }
        yield ErrorType.SYSTEM;
      }
    };
  }
}