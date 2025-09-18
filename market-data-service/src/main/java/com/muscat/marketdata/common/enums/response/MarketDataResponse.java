package com.muscat.marketdata.common.enums.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MarketDataResponse {

    // 성공 응답
    PRICE_DATA_RETRIEVED("200", "가격 데이터 조회 성공", HttpStatus.OK),
    CANDLE_DATA_RETRIEVED("200", "캔들 데이터 조회 성공", HttpStatus.OK),
    DIVIDEND_DATA_RETRIEVED("200", "배당 데이터 조회 성공", HttpStatus.OK),
    FX_RATE_RETRIEVED("200", "환율 데이터 조회 성공", HttpStatus.OK),
    HISTORICAL_DATA_GENERATED("200", "과거 데이터 생성 성공", HttpStatus.OK),

    // 클라이언트 오류
    INVALID_SYMBOL("400", "유효하지 않은 종목 코드입니다", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE("400", "유효하지 않은 날짜 범위입니다", HttpStatus.BAD_REQUEST),


    // 리소스 없음
    SYMBOL_NOT_FOUND("404", "종목을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRICE_DATA_NOT_FOUND("404", "가격 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    DIVIDEND_DATA_NOT_FOUND("404", "배당 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    FX_RATE_NOT_FOUND("404", "환율 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // 유효성 검증 오류
    MISSING_REQUIRED_FIELDS("422", "필수 필드가 누락되었습니다", HttpStatus.UNPROCESSABLE_ENTITY),

    // 외부 서비스 오류 - 실제 사용되는 것들
    YAHOO_FINANCE_ERROR("502", "Yahoo Finance 서비스 오류", HttpStatus.BAD_GATEWAY),
    ALPHA_VANTAGE_ERROR("502", "Alpha Vantage 서비스 오류", HttpStatus.BAD_GATEWAY),
    KOREA_EXIM_ERROR("502", "한국수출입은행 서비스 오류", HttpStatus.BAD_GATEWAY),

    // 서버 내부 오류
    INTERNAL_SERVER_ERROR("500", "시장 데이터 서비스 내부 오류", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}