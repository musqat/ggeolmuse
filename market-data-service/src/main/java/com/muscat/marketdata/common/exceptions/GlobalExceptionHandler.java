package com.muscat.marketdata.common.exceptions;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.commonlib.util.ProblemDetailUtils;
import com.muscat.commonlib.enums.ErrorType;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {
    com.muscat.marketdata.api.MarketController.class,
    com.muscat.marketdata.feed.DataCollectionController.class
})
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String currentProfile;

    @ExceptionHandler(YahooFinanceException.class)
    public ResponseEntity<ProblemDetail> handleYahooFinanceException(YahooFinanceException e, HttpServletRequest request) {
        log.error("[YAHOO FINANCE ERROR] {}", e.getMessage(), e);

        Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
        ProblemDetail problem = ProblemDetailUtils.createProblem(
            MarketDataResponse.YAHOO_FINANCE_ERROR.getHttpStatus(),
            MarketDataResponse.YAHOO_FINANCE_ERROR.getMessage() + ": " + e.getMessage(),
            "YAHOO_FINANCE_ERROR",
            request.getRequestURI(),
            "Yahoo Finance Service Error",
            properties
        );

        return ResponseEntity.status(MarketDataResponse.YAHOO_FINANCE_ERROR.getHttpStatus()).body(problem);
    }

    @ExceptionHandler(AlphaVantageException.class)
    public ResponseEntity<ProblemDetail> handleAlphaVantageException(AlphaVantageException e, HttpServletRequest request) {
        log.error("[ALPHA VANTAGE ERROR] {}", e.getMessage(), e);

        Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
        ProblemDetail problem = ProblemDetailUtils.createProblem(
            MarketDataResponse.ALPHA_VANTAGE_ERROR.getHttpStatus(),
            MarketDataResponse.ALPHA_VANTAGE_ERROR.getMessage() + ": " + e.getMessage(),
            "ALPHA_VANTAGE_ERROR",
            request.getRequestURI(),
            "Alpha Vantage Service Error",
            properties
        );

        return ResponseEntity.status(MarketDataResponse.ALPHA_VANTAGE_ERROR.getHttpStatus()).body(problem);
    }

    @ExceptionHandler(FxRateException.class)
    public ResponseEntity<ProblemDetail> handleFxRateException(FxRateException e, HttpServletRequest request) {
        log.error("[FX RATE ERROR] {}", e.getMessage(), e);

        Map<String, Object> properties = Map.of("errorType", ErrorType.EXTERNAL_SERVICE.name());
        ProblemDetail problem = ProblemDetailUtils.createProblem(
            MarketDataResponse.KOREA_EXIM_ERROR.getHttpStatus(),
            MarketDataResponse.KOREA_EXIM_ERROR.getMessage() + ": " + e.getMessage(),
            "FX_RATE_ERROR",
            request.getRequestURI(),
            "FX Rate Service Error",
            properties
        );

        return ResponseEntity.status(MarketDataResponse.KOREA_EXIM_ERROR.getHttpStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        return super.handleValidationException(e, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[MISSING PARAMETER] {}", e.getParameterName());

        String message = String.format("필수 파라미터가 누락되었습니다: %s", e.getParameterName());

        Map<String, Object> properties = Map.of(
            "parameterName", e.getParameterName(),
            "errorType", ErrorType.VALIDATION.name()
        );
        ProblemDetail problem = ProblemDetailUtils.createProblem(
            MarketDataResponse.MISSING_REQUIRED_FIELDS.getHttpStatus(),
            message,
            "MISSING_PARAMETER",
            request.getRequestURI(),
            "Missing Parameter Error",
            properties
        );

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
        log.error("[SYSTEM ERROR] 예상치 못한 오류 발생", e);

        // 개발 환경에서는 상세한 에러 메시지 제공
        String message = isDevelopmentEnvironment() 
            ? String.format("%s - %s", e.getMessage(), e.getClass().getSimpleName())
            : "시장 데이터 서비스에 문제가 발생했습니다. 관리자에게 문의해주세요.";

        Map<String, Object> properties = Map.of("errorType", ErrorType.SYSTEM.name());
        ProblemDetail problem = ProblemDetailUtils.createProblem(
            MarketDataResponse.INTERNAL_SERVER_ERROR.getHttpStatus(),
            message,
            "INTERNAL_SERVER_ERROR",
            request.getRequestURI(),
            "Internal Server Error",
            properties
        );

        return ResponseEntity.status(MarketDataResponse.INTERNAL_SERVER_ERROR.getHttpStatus()).body(problem);
    }

    private boolean isDevelopmentEnvironment() {
        // 개발 환경 판단 로직
        String[] activeProfiles = {"dev", "development", "local"};
        return java.util.Arrays.asList(activeProfiles).contains(currentProfile);
    }
}