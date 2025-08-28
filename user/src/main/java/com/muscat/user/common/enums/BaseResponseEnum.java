package com.muscat.user.common.enums;

import org.springframework.http.HttpStatus;

// API 응답 상태를 정의하는 인터페이스
public interface BaseResponseEnum {
  String getCode();           // 상태 코드 (200, 400, 500 등)
  String getMessage();        // 응답 메시지
  HttpStatus getHttpStatus(); // HTTP 상태
}