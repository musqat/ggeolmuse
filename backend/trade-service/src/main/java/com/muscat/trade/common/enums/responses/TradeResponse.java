package com.muscat.trade.common.enums.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TradeResponse {

    // === 성공 응답 (200번대) ===
    BUY_ORDER_COMPLETED("200", "매수 주문이 완료되었습니다.", HttpStatus.OK),
    SELL_ORDER_COMPLETED("200", "매도 주문이 완료되었습니다.", HttpStatus.OK),
    PORTFOLIO_RETRIEVED("200", "포트폴리오 조회가 완료되었습니다.", HttpStatus.OK),
    TRADE_HISTORY_RETRIEVED("200", "거래 내역 조회가 완료되었습니다.", HttpStatus.OK),
    HOLDINGS_RETRIEVED("200", "보유 주식 조회가 완료되었습니다.", HttpStatus.OK),
    TRADE_STATISTICS_RETRIEVED("200", "거래 통계 조회가 완료되었습니다.", HttpStatus.OK),
    BALANCE_UPDATE_SUCCESS("200", "잔액 변경이 완료되었습니다.", HttpStatus.OK),
    DIVIDEND_CALCULATED("200", "배당금 계산이 완료되었습니다.", HttpStatus.OK),
    COMPENSATION_TRANSACTION_SUCCESS("200", "보상 트랜잭션이 완료되었습니다.", HttpStatus.OK),

    // === 클라이언트 오류 (400번대) ===
    INVALID_TRADE_REQUEST("400", "잘못된 거래 요청입니다.", HttpStatus.BAD_REQUEST),
    INVALID_SYMBOL("400", "유효하지 않은 심볼입니다.", HttpStatus.BAD_REQUEST),
    INVALID_QUANTITY("400", "수량은 0보다 큰 정수여야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_PRICE("400", "가격은 0보다 큰 값이어야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_TRADE_TYPE("400", "거래 유형이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_ACCOUNT_ID("400", "계좌 ID가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE("400", "잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_USD_BALANCE("400", "USD 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_KRW_BALANCE("400", "KRW 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_HOLDINGS("400", "보유 수량이 부족합니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_SELLABLE_QUANTITY("400", "매도 가능한 수량이 부족합니다.", HttpStatus.BAD_REQUEST),
    MINIMUM_TRADE_AMOUNT_NOT_MET("400", "최소 거래 금액을 충족하지 않습니다.", HttpStatus.BAD_REQUEST),
    MAXIMUM_TRADE_AMOUNT_EXCEEDED("400", "최대 거래 금액을 초과했습니다.", HttpStatus.BAD_REQUEST),
    TRADE_LIMIT_EXCEEDED("400", "일일 거래 한도를 초과했습니다.", HttpStatus.BAD_REQUEST),
    MARKET_CLOSED("400", "장이 마감되어 거래할 수 없습니다.", HttpStatus.BAD_REQUEST),
    TRADING_SUSPENDED("400", "해당 종목은 거래 정지 상태입니다.", HttpStatus.BAD_REQUEST),
    FRACTIONAL_SHARES_NOT_ALLOWED("400", "소수점 주식 거래는 지원되지 않습니다.", HttpStatus.BAD_REQUEST),

    // === 인증/인가 오류 (401, 403) ===
    AUTHENTICATION_REQUIRED("401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("401", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("401", "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_ACCESS_DENIED("403", "계좌에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    TRADING_NOT_AUTHORIZED("403", "거래 권한이 없습니다.", HttpStatus.FORBIDDEN),
    ACCOUNT_SUSPENDED("403", "계좌가 정지되어 거래할 수 없습니다.", HttpStatus.FORBIDDEN),

    // === 리소스 없음 (404) ===
    ACCOUNT_NOT_FOUND("404", "계좌를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    TRADE_NOT_FOUND("404", "거래 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SYMBOL_NOT_FOUND("404", "해당 종목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    HOLDINGS_NOT_FOUND("404", "보유 주식을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PORTFOLIO_NOT_FOUND("404", "포트폴리오를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // === 충돌 오류 (409) ===
    DUPLICATE_TRADE_REQUEST("409", "중복된 거래 요청입니다.", HttpStatus.CONFLICT),
    CONCURRENT_TRADE_CONFLICT("409", "동시 거래로 인한 충돌이 발생했습니다.", HttpStatus.CONFLICT),

    // === 데이터 검증 오류 (422) ===
    INVALID_TRADE_DATE("422", "거래 날짜가 유효하지 않습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    FUTURE_DATE_NOT_ALLOWED("422", "미래 날짜로는 거래할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    WEEKEND_TRADING_NOT_ALLOWED("422", "주말에는 거래할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    HOLIDAY_TRADING_NOT_ALLOWED("422", "휴일에는 거래할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),

    // === 요청 한도 초과 (429) ===
    TRADE_REQUEST_RATE_LIMIT("429", "거래 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    API_RATE_LIMIT_EXCEEDED("429", "API 호출 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),

    // === 외부 서비스 오류 (502, 503) ===
    MARKET_DATA_SERVICE_ERROR("502", "시장 데이터 서비스 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    USER_SERVICE_ERROR("502", "유저 서비스 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    PRICE_DATA_UNAVAILABLE("503", "가격 정보를 가져올 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    MARKET_DATA_DELAYED("503", "시장 데이터가 지연되고 있습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    EXTERNAL_BROKER_ERROR("503", "외부 브로커 서비스에 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE),

    // === 서버 내부 오류 (500) ===
    TRANSACTION_FAILED("500", "거래 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    COMPENSATION_TRANSACTION_FAILED("500", "거래 실패 후 보상 트랜잭션도 실패했습니다. 수동 개입이 필요합니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("500", "데이터베이스 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CALCULATION_ERROR("500", "수익률 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    PORTFOLIO_CALCULATION_ERROR("500", "포트폴리오 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DIVIDEND_CALCULATION_ERROR("500", "배당금 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CURRENCY_CONVERSION_ERROR("500", "환율 변환 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    FIFO_CALCULATION_ERROR("500", "FIFO 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    BALANCE_UPDATE_FAILED("500", "잔액 업데이트에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    HOLDINGS_UPDATE_FAILED("500", "보유 주식 업데이트에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    TRADE_HISTORY_SAVE_FAILED("500", "거래 내역 저장에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR("500", "서버에 예기치 못한 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}