package com.muscat.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BaseResponseEnum {
    
    // 성공
    SUCCESS(200, "요청이 성공적으로 처리되었습니다"),
    CREATED(201, "리소스가 성공적으로 생성되었습니다"),
    
    // 클라이언트 오류
    BAD_REQUEST(400, "잘못된 요청입니다"),
    UNAUTHORIZED(401, "인증이 필요합니다"),
    FORBIDDEN(403, "접근 권한이 없습니다"),
    NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(405, "허용되지 않은 HTTP 메서드입니다"),
    CONFLICT(409, "리소스 충돌이 발생했습니다"),
    UNPROCESSABLE_ENTITY(422, "처리할 수 없는 요청입니다"),
    TOO_MANY_REQUESTS(429, "너무 많은 요청이 발생했습니다"),
    
    // 서버 오류
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다"),
    BAD_GATEWAY(502, "게이트웨이 오류가 발생했습니다"),
    SERVICE_UNAVAILABLE(503, "서비스를 사용할 수 없습니다"),
    GATEWAY_TIMEOUT(504, "게이트웨이 시간 초과입니다"),
    
    // 거래 전용 응답
    TRADE_BUY_SUCCESS(200, "매수 주문이 성공적으로 처리되었습니다"),
    TRADE_SELL_SUCCESS(200, "매도 주문이 성공적으로 처리되었습니다"),
    TRADE_VALIDATION_SUCCESS(200, "거래 가능 여부 확인이 완료되었습니다"),
    INSUFFICIENT_BALANCE(400, "계좌 잔액이 부족합니다"),
    INSUFFICIENT_HOLDINGS(400, "보유 수량이 부족합니다"),
    INSUFFICIENT_SELLABLE_QUANTITY(400, "매도 가능한 수량이 부족합니다"),
    ACCOUNT_NOT_FOUND(404, "계좌를 찾을 수 없습니다"),
    MARKET_DATA_SERVICE_ERROR(503, "시장 데이터 서비스에 문제가 발생했습니다"),
    USER_SERVICE_ERROR(503, "사용자 서비스에 문제가 발생했습니다");
    
    private final int code;
    private final String message;
}