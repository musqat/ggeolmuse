package com.muscat.marketdata.common.exception;

import com.muscat.commonlib.exception.BaseExceptionHandler;
import com.muscat.commonlib.util.ProblemDetailUtils;
import com.muscat.commonlib.enums.ErrorType;
import com.muscat.marketdata.MarketDataApplication;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 컨트롤러의 예외 응답
@RestControllerAdvice(basePackageClasses = MarketDataApplication.class)
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

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

    // 컨트롤러에 붙은 @Validated 가 경로 변수 · 쿼리 파라미터를 검사하다 던진다. 요청 값이 틀린 것이라 400 이다
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String parameters = e.getConstraintViolations().stream()
            .map(GlobalExceptionHandler::parameterName)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
        log.warn("[INVALID PARAMETER] {}", parameters);

        Map<String, Object> properties = Map.of(
            "parameterName", parameters,
            "errorType", ErrorType.VALIDATION.name()
        );
        ProblemDetail problem = ProblemDetailUtils.createProblem(
            HttpStatus.BAD_REQUEST,
            String.format("요청 값이 올바르지 않습니다: %s", parameters),
            "INVALID_PARAMETER",
            request.getRequestURI(),
            "Invalid Parameter Error",
            properties
        );

        return ResponseEntity.badRequest().body(problem);
    }

    // getCurrentPrice.symbol · getMultipleOHLCPrices.symbols[0].<list element> 에서 파라미터 이름만 꺼낸다
    private static String parameterName(ConstraintViolation<?> violation) {
        String name = violation.getPropertyPath().toString();
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER) {
                name = node.getName();
            }
        }
        return name;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
        return systemError(e, request, "시장 데이터 서비스에 문제가 발생했습니다.");
    }
}
