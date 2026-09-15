package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.responses.SocialResponse;

public class SocialLoginException extends BusinessException {

  public SocialLoginException(SocialResponse response) {
    super(response);
  }
}
