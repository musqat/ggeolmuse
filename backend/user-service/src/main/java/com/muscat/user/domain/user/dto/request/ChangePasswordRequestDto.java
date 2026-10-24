package com.muscat.user.domain.user.dto.request;

import com.muscat.user.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "비밀번호 변경 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequestDto {
  @Schema(description = "현재 비밀번호", example = "oldPassword123!", required = true)
  @NotBlank(message = "현재 비밀번호는 필수입니다")
  private String currentPassword;

  @Schema(description = "새 비밀번호", example = "newPassword123!", required = true, minLength = 8, maxLength = 100)
  @ValidPassword
  private String newPassword;
}
