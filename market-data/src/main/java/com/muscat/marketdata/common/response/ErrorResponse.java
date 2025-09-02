package com.muscat.marketdata.common.response;

import com.muscat.marketdata.common.enums.type.ErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    
    // Market Data 특화 필드들
    private String provider;
    private String symbol;
    private String dataType;
    private Integer retryAfterSeconds;

    // 기본 에러 응답
    public static ErrorResponse of(String path, int status, String message, ErrorType errorType) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(errorType.name())
                .message(message)
                .path(path)
                .build();
    }
}