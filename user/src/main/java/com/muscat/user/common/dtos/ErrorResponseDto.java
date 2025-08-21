package com.muscat.user.common.dtos;

import com.muscat.user.common.enums.type.ErrorType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class ErrorResponseDto {

  private String path;
  private HttpStatus status;
  private String message;
  private LocalDateTime timestamp;
  private ErrorType errorType;

  public static ErrorResponseDto of(String path, HttpStatus status, String message,
      ErrorType errorType) {
    return new ErrorResponseDto(path, status, message, LocalDateTime.now(), errorType);
  }
}