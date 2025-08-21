package com.muscat.user.common.enums;

import org.springframework.http.HttpStatus;

public interface BaseResponseEnum {
  String getCode();
  String getMessage();
  HttpStatus getHttpStatus();
}