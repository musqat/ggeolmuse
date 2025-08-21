package com.muscat.user.common.enums.type;

public enum ErrorType {
  VALIDATION,     // 입력값 검증 오류
  BUSINESS,       // 비즈니스 로직 오류
  NOT_FOUND,      // 리소스 없음
  CONFLICT,       // 중복/충돌
  UNAUTHORIZED,   // 인증 실패
  FORBIDDEN,      // 권한 없음
  SYSTEM          // 시스템 오류

}
