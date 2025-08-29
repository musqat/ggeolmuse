package com.muscat.trade.common.enums;

import org.springframework.http.HttpStatus;

public interface BaseResponseEnum {
  String getCode();           // 상태 코드 (200, 400, 500 등)
  String getMessage();        // 응답 메시지
  HttpStatus getHttpStatus(); // HTTP 상태
}