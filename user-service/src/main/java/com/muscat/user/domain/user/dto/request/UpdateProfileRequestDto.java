package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "프로필 업데이트 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequestDto {

  @Schema(description = "닉네임", example = "김투자자", required = false)
  @Size(max = 20, message = "닉네임은 최대 20자 이하이어야 합니다")
  @Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$", message = "닉네임은 영문, 숫자, 한글, 언더스코어만 사용 가능합니다")
  private String nickname;
}
