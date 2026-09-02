package com.muscat.commonlib.enums;

/**
 * 공통 에러 유형 분류
 */
public enum ErrorType {

  // 기본 에러 타입
  VALIDATION,       // 입력값 검증 오류
  BUSINESS,         // 비즈니스 로직 오류
  UNAUTHORIZED,     // 인증 실패
  FORBIDDEN,        // 권한 없음
  NOT_FOUND,        // 리소스 없음
  CONFLICT,         // 중복/충돌
  SYSTEM,           // 시스템 오류

  // 외부 서비스 관련
  EXTERNAL_SERVICE, // 외부 서비스 오류
  NETWORK,          // 네트워크 오류
  TIMEOUT,          // 시간 초과
  RATE_LIMIT        // 호출 제한 초과
}
