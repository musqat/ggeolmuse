package com.muscat.user.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequestDto {

  @Size(max = 20, message = "닉네임은 최대 20자 이하이어야 합니다")
  @Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$", message = "닉네임은 영문, 숫자, 한글, 언더스코어만 사용 가능합니다")
  private String nickname;
}
