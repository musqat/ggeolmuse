package com.muscat.trade.common.responses;

import com.muscat.trade.common.enums.type.ErrorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ErrorResponse {

  private String path;
  private HttpStatus status;
  private String message;
  private LocalDateTime timestamp;
  private ErrorType errorType;

  public static ErrorResponse of(String path, HttpStatus status, String message, ErrorType errorType) {
    return new ErrorResponse(path, status, message, LocalDateTime.now(), errorType);
  }
}