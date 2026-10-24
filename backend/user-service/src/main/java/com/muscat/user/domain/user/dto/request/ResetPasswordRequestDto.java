package com.muscat.user.domain.user.dto.request;

import com.muscat.user.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

  @Schema(description = "새 비밀번호", example = "newPassword123!", required = true, minLength = 8, maxLength = 100)
  @ValidPassword
  private String newPassword;
}
