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
  // 등록/변경과 같은 규칙. 예전 정규식은 특수문자를 [@$!%*?&] 로만 허용해
  // 그 밖의 특수문자로 등록한 비번을 재설정 때 못 쓰는 불일치가 있었다.
  @Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
    message = "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다")
  private String newPassword;
}
