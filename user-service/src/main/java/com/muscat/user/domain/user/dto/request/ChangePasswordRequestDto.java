package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "비밀번호 변경 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequestDto {
  @Schema(description = "새 비밀번호", example = "newPassword123!", required = true)
  @NotBlank(message = "새 비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
  // 변경 경로에도 등록/재설정과 같은 복잡도를 건다. 없으면 여기로 약한 비번을 우회 설정할 수 있다.
  @Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
    message = "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다")
  private String newPassword;
}
