package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "사용자 회원가입 요청")
public class RegisterRequestDto {

  @Schema(description = "사용자 이메일", example = "user@ggeolmuse.com", required = true)
  @NotBlank(message = "이메일은 필수입니다")
  @Email(message = "올바른 이메일 형식이 아닙니다")
  private String email;

  @Schema(description = "비밀번호", example = "password123!", required = true, minLength = 8)
  @NotBlank(message = "비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
  // 예전엔 최소 길이만 봐서 "aaaaaaaa" 같은 약한 비번이 통과했다.
  // 영문·숫자·특수문자를 각각 하나 이상 요구한다. (@NotBlank 가 null 을 막으므로 여기선 형식만)
  @Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
    message = "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다")
  private String password;

  @Schema(description = "사용자 닉네임", example = "투자왕김과장", required = true, minLength = 2, maxLength = 20)
  @NotBlank
  @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하이어야 합니다")
  private String nickname;

}
