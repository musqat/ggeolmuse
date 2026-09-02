package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "계정 삭제 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class DeleteAccountRequestDto {
  @Schema(description = "비밀번호", example = "password123", required = true)
  @NotBlank(message = "비밀번호는 필수입니다")
  private String password;
}
