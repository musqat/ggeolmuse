package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.responses.UserResponse;

public class UserException extends BusinessException {

  public UserException(UserResponse response) {
    super(response);
  }
}
