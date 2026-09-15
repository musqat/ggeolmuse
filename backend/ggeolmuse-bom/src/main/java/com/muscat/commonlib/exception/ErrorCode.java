package com.muscat.commonlib.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 응답 enum 이 구현하는 에러 코드. name() 은 Enum 이 채운다
 */
public interface ErrorCode {

  String name();

  String getMessage();

  HttpStatus getHttpStatus();
}
