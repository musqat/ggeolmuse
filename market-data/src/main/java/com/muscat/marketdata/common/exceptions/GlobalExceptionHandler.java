package com.muscat.marketdata.common.exceptions;

import com.muscat.marketdata.common.enums.BaseResponseEnum;
import com.muscat.marketdata.common.enums.type.ErrorType;
import com.muscat.marketdata.common.logging.MarketDataLogger;
import com.muscat.marketdata.common.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final MarketDataLogger marketDataLogger;

    @ExceptionHandler(ApiRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleApiRateLimit(
            ApiRateLimitException ex, WebRequest request) {
        
        log.warn("API 호출 제한 발생: provider={}, retryAfter={}초", 
                ex.getProvider(), ex.getRetryAfterSeconds());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.RATE_LIMIT_EXCEEDED.getCode())
                .error(ErrorType.RATE_LIMIT_EXCEEDED.name())
                .message(ex.getMessage())
                .provider(ex.getProvider())
                .retryAfterSeconds(ex.getRetryAfterSeconds())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDataNotFound(
            DataNotFoundException ex, WebRequest request) {
        
        log.info("데이터 없음: symbol={}, dataType={}", ex.getSymbol(), ex.getDataType());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.DATA_NOT_FOUND.getCode())
                .error(ErrorType.DATA_NOT_FOUND.name())
                .message(ex.getMessage())
                .symbol(ex.getSymbol())
                .dataType(ex.getDataType())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DataParsingException.class)
    public ResponseEntity<ErrorResponse> handleDataParsing(
            DataParsingException ex, WebRequest request) {
        
        log.error("데이터 파싱 실패: provider={}, dataType={}, error={}", 
                ex.getProvider(), ex.getDataType(), ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.DATA_PARSING_ERROR.getCode())
                .error(ErrorType.DATA_PARSING_ERROR.name())
                .message(ex.getMessage())
                .provider(ex.getProvider())
                .dataType(ex.getDataType())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler({YahooFinanceException.class})
    public ResponseEntity<ErrorResponse> handleProviderException(
            MarketDataException ex, WebRequest request) {
        
        log.error("마켓 데이터 제공자 오류 발생: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.PROVIDER_ERROR.getCode())
                .error(ErrorType.EXTERNAL_SERVICE_ERROR.name())
                .message("외부 데이터 제공자 오류가 발생했습니다: " + ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(MarketDataException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataException(
            MarketDataException ex, WebRequest request) {
        
        log.error("마켓 데이터 오류: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.INTERNAL_SERVER_ERROR.getCode())
                .error(ErrorType.DATA_NOT_FOUND.name())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("잘못된 요청 파라미터: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.BAD_REQUEST.getCode())
                .error(ErrorType.INVALID_PARAMETER.name())
                .message("잘못된 요청 파라미터: " + ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("예상치 못한 오류 발생", ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(BaseResponseEnum.INTERNAL_SERVER_ERROR.getCode())
                .error(ErrorType.INTERNAL_ERROR.name())
                .message("서버 내부 오류가 발생했습니다")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }


}