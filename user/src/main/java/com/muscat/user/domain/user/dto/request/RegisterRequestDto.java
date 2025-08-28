package com.muscat.user.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterRequestDto {

  @NotBlank(message = "이메일은 필수입니다")
  @Email(message = "올바른 이메일 형식이 아닙니다")
  private String email;

  @NotBlank(message = "비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
  private String password;

  @NotBlank
  @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하이어야 합니다")
  private String nickname;

}
