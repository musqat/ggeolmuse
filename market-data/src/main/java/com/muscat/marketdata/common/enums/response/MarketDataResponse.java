package com.muscat.marketdata.common.enums.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MarketDataResponse {

    // === 성공 응답 (200번대) ===
    PRICE_DATA_RETRIEVED("200", "가격 데이터 조회가 완료되었습니다.", HttpStatus.OK),
    CANDLE_DATA_RETRIEVED("200", "캔들 데이터 조회가 완료되었습니다.", HttpStatus.OK),
    DIVIDEND_DATA_RETRIEVED("200", "배당 데이터 조회가 완료되었습니다.", HttpStatus.OK),
    FX_RATE_RETRIEVED("200", "환율 데이터 조회가 완료되었습니다.", HttpStatus.OK),
    ASSET_INFO_RETRIEVED("200", "자산 정보 조회가 완료되었습니다.", HttpStatus.OK),
    MARKET_STATUS_RETRIEVED("200", "시장 상태 조회가 완료되었습니다.", HttpStatus.OK),
    DATA_SYNC_COMPLETED("200", "데이터 동기화가 완료되었습니다.", HttpStatus.OK),
    BATCH_PROCESSING_COMPLETED("200", "일괄 처리가 완료되었습니다.", HttpStatus.OK),

    // === 클라이언트 오류 (400번대) ===
    INVALID_SYMBOL("400", "유효하지 않은 종목 코드입니다.", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE("400", "유효하지 않은 날짜 범위입니다.", HttpStatus.BAD_REQUEST),
    INVALID_DATE_FORMAT("400", "날짜 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    START_DATE_AFTER_END_DATE("400", "시작 날짜는 종료 날짜보다 이전이어야 합니다.", HttpStatus.BAD_REQUEST),
    FUTURE_DATE_NOT_ALLOWED("400", "미래 날짜는 조회할 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVALID_CURRENCY_PAIR("400", "유효하지 않은 통화 쌍입니다.", HttpStatus.BAD_REQUEST),
    INVALID_DATA_TYPE("400", "유효하지 않은 데이터 타입입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PROVIDER_TYPE("400", "유효하지 않은 데이터 제공업체입니다.", HttpStatus.BAD_REQUEST),
    DATE_RANGE_TOO_LARGE("400", "조회 가능한 날짜 범위를 초과했습니다.", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MARKET("400", "지원하지 않는 시장입니다.", HttpStatus.BAD_REQUEST),

    // === 인증/인가 오류 (401, 403) ===
    API_KEY_REQUIRED("401", "API 키가 필요합니다.", HttpStatus.UNAUTHORIZED),
    INVALID_API_KEY("401", "유효하지 않은 API 키입니다.", HttpStatus.UNAUTHORIZED),
    API_KEY_EXPIRED("401", "API 키가 만료되었습니다.", HttpStatus.UNAUTHORIZED),
    INSUFFICIENT_PERMISSIONS("403", "권한이 부족합니다.", HttpStatus.FORBIDDEN),
    DATA_ACCESS_RESTRICTED("403", "데이터 접근이 제한되어 있습니다.", HttpStatus.FORBIDDEN),

    // === 리소스 없음 (404) ===
    SYMBOL_NOT_FOUND("404", "종목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRICE_DATA_NOT_FOUND("404", "가격 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DIVIDEND_DATA_NOT_FOUND("404", "배당 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    FX_RATE_NOT_FOUND("404", "환율 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    HISTORICAL_DATA_NOT_AVAILABLE("404", "해당 날짜의 과거 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MARKET_DATA_NOT_AVAILABLE("404", "시장 데이터를 사용할 수 없습니다.", HttpStatus.NOT_FOUND),

    // === 충돌 오류 (409) ===
    DATA_ALREADY_EXISTS("409", "데이터가 이미 존재합니다.", HttpStatus.CONFLICT),
    SYNC_IN_PROGRESS("409", "데이터 동기화가 진행 중입니다.", HttpStatus.CONFLICT),

    // === 데이터 검증 오류 (422) ===
    INVALID_PRICE_DATA("422", "유효하지 않은 가격 데이터입니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    MISSING_REQUIRED_FIELDS("422", "필수 필드가 누락되었습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    INCONSISTENT_DATA("422", "일관성이 없는 데이터입니다.", HttpStatus.UNPROCESSABLE_ENTITY),

    // === 요청 한도 초과 (429) ===
    API_RATE_LIMIT_EXCEEDED("429", "API 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    YAHOO_FINANCE_RATE_LIMIT("429", "Yahoo Finance API 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),
    ALPHA_VANTAGE_RATE_LIMIT("429", "Alpha Vantage API 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),
    KOREA_EXIM_RATE_LIMIT("429", "한국수출입은행 API 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),

    // === 외부 서비스 오류 (502, 503) ===
    YAHOO_FINANCE_ERROR("502", "Yahoo Finance 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    ALPHA_VANTAGE_ERROR("502", "Alpha Vantage 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    KOREA_EXIM_ERROR("502", "한국수출입은행 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    EXTERNAL_API_ERROR("502", "외부 API 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    YAHOO_FINANCE_UNAVAILABLE("503", "Yahoo Finance 서비스를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    ALPHA_VANTAGE_UNAVAILABLE("503", "Alpha Vantage 서비스를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    KOREA_EXIM_UNAVAILABLE("503", "한국수출입은행 서비스를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    MARKET_DATA_SERVICE_UNAVAILABLE("503", "시장 데이터 서비스를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    DATA_PROVIDER_MAINTENANCE("503", "데이터 제공업체가 점검 중입니다.", HttpStatus.SERVICE_UNAVAILABLE),

    // === 게이트웨이 시간 초과 (504) ===
    DATA_FETCH_TIMEOUT("504", "데이터 조회 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT),
    EXTERNAL_API_TIMEOUT("504", "외부 API 응답 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT),

    // === 서버 내부 오류 (500) ===
    DATA_PARSING_ERROR("500", "데이터 파싱 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATA_PROCESSING_ERROR("500", "데이터 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATA_STORAGE_ERROR("500", "데이터 저장 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CALCULATION_ERROR("500", "계산 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    FX_RATE_CALCULATION_ERROR("500", "환율 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_CONNECTION_ERROR("500", "데이터베이스 연결에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_ERROR("500", "캐시 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    BATCH_PROCESSING_ERROR("500", "일괄 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATA_SYNC_ERROR("500", "데이터 동기화 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CONFIGURATION_ERROR("500", "설정 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR("500", "시장 데이터 서비스에 예기치 못한 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}