package com.muscat.backtest.common.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

  private LocalDateTime timestamp;
  private int status;
  private String error;
  private String message;
  private String path;

  // Backtest 특화 필드들
  private String symbol;
  private String simulationType;
  private String errorCode;
  private Object details;

  // 기본 에러 응답
  public static ErrorResponse of(String path, int status, String message, String errorCode) {
    return ErrorResponse.builder()
        .timestamp(LocalDateTime.now())
        .status(status)
        .error(errorCode)
        .message(message)
        .path(path)
        .errorCode(errorCode)
        .build();
  }
}