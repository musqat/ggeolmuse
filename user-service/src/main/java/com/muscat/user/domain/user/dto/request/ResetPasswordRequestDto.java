package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 비밀번호 재설정 요청 DTO
 */
@Schema(description = "비밀번호 재설정 정보")
@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordRequestDto {

  @Schema(description = "재설정 토큰", example = "abc123...", required = true)
  @NotBlank(message = "토큰은 필수입니다")
  private String token;

  @Schema(description = "새 비밀번호", example = "newPassword123!", required = true)
  @NotBlank(message = "비밀번호는 필수입니다")
  @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다")
  @Pattern(
    regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
    message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다"
  )
  private String newPassword;
}
