package com.muscat.user.common.exceptions;

import com.muscat.user.common.dtos.ErrorResponseDto;
import com.muscat.user.common.enums.type.ErrorType;
import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.common.responses.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // 사용자 관련 비즈니스 예외 처리
  @ExceptionHandler(UserException.class)
  public ResponseEntity<ErrorResponseDto> handleUserException(UserException e,
      HttpServletRequest request) {
    log.warn("[USER ERROR] {}", e.getMessage());

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        ErrorType.BUSINESS
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(errorResponse);
  }

  // 소셜 로그인 관련 비즈니스 예외 처리
  @ExceptionHandler(SocialLoginException.class)
  public ResponseEntity<ErrorResponseDto> handleSocialLoginException(SocialLoginException e,
      HttpServletRequest request) {
    log.error("[SOCIAL LOGIN ERROR] {}", e.getMessage(), e);

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        ErrorType.BUSINESS
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(errorResponse);
  }

  // Keycloak 관련 시스템 예외 처리
  @ExceptionHandler(KeycloakException.class)
  public ResponseEntity<ErrorResponseDto> handleKeycloakException(KeycloakException e,
      HttpServletRequest request) {
    log.error("[KEYCLOAK ERROR] {}", e.getMessage(), e);

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        ErrorType.KEYCLOAK
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(errorResponse);
  }

  // 인증 관련 예외 처리
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponseDto> handleAuthenticationException(AuthenticationException e,
      HttpServletRequest request) {
    log.warn("[AUTH ERROR] {}", e.getMessage());

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        ErrorType.UNAUTHORIZED
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(errorResponse);
  }
  // 계좌 관련 비즈니스 예외 처리
  @ExceptionHandler(AccountException.class)
  public ResponseEntity<ErrorResponseDto> handleAccountException(AccountException e,
      HttpServletRequest request) {
    log.warn("[ACCOUNT ERROR] {}", e.getMessage());

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        ErrorType.BUSINESS
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(errorResponse);
  }

  // 계좌 거래 내역 관련 예외
  @ExceptionHandler(AccountHistoryException.class)
  public ResponseEntity<ErrorResponseDto> handleAccountHistoryException(AccountHistoryException e,
      HttpServletRequest request) {
    log.warn("[ACCOUNT HISTORY ERROR] {}", e.getMessage());

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        e.getErrorCode().getHttpStatus(),
        e.getMessage(),
        ErrorType.BUSINESS
    );

    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(errorResponse);
  }

  // 유효성 검사 실패 예외 처리
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
      MethodArgumentNotValidException e) {
    log.warn("[VALIDATION ERROR] 입력값 검증 실패");

    Map<String, String> errors = new HashMap<>();
    e.getBindingResult().getAllErrors().forEach((error) -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });

    return ResponseEntity.badRequest()
        .body(ApiResponse.error(UserResponse.INVALID_INPUT, errors));
  }

  // 필수 파라미터 누락 예외 처리
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponseDto> handleMissingParameterException(
      MissingServletRequestParameterException e, HttpServletRequest request) {
    log.warn("[MISSING PARAMETER] {}", e.getParameterName());

    String message = String.format("필수 파라미터가 누락되었습니다: %s", e.getParameterName());

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        UserResponse.INVALID_INPUT.getHttpStatus(),
        message,
        ErrorType.VALIDATION
    );

    return ResponseEntity.badRequest()
        .body(errorResponse);
  }

  // 기타 모든 예외 처리
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDto> handleGenericException(Exception e,
      HttpServletRequest request) {
    log.error("[SYSTEM ERROR] 예상치 못한 오류 발생", e);

    ErrorResponseDto errorResponse = ErrorResponseDto.of(
        request.getRequestURI(),
        UserResponse.INTERNAL_SERVER_ERROR.getHttpStatus(),
        "서버에 문제가 발생했습니다. 관리자에게 문의해주세요.",
        ErrorType.SYSTEM
    );

    return ResponseEntity.status(UserResponse.INTERNAL_SERVER_ERROR.getHttpStatus())
        .body(errorResponse);
  }
}