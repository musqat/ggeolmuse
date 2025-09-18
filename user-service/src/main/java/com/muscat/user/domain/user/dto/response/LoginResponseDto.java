package com.muscat.user.domain.user.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LoginResponseDto {
  private String accessToken;
  private String refreshToken;
  private int expiresIn;
  private String tokenType;
}
