package com.muscat.marketdata.common.enums;

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
    
    // 마켓 데이터 전용 응답
    DATA_NOT_FOUND(404, "요청한 데이터를 찾을 수 없습니다"),
    RATE_LIMIT_EXCEEDED(429, "API 호출 제한을 초과했습니다"),
    PROVIDER_ERROR(503, "외부 데이터 제공자 오류입니다"),
    DATA_PARSING_ERROR(422, "데이터 파싱 중 오류가 발생했습니다");
    
    private final int code;
    private final String message;
}