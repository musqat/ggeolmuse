package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 비밀번호 찾기 요청 DTO
 */
@Schema(description = "비밀번호 재설정 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class ForgotPasswordRequestDto {

  @Schema(description = "이메일", example = "user@example.com", required = true)
  @NotBlank(message = "이메일은 필수입니다")
  @Email(message = "올바른 이메일 형식이 아닙니다")
  private String email;
}
