package com.muscat.user.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.response.UserResponse;

public class UserException extends BusinessException {

  public UserException(UserResponse response) {
    super(response);
  }

  public UserException(UserResponse response, Throwable cause) {
    super(response, cause);
  }
}
