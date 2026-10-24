package com.muscat.user.domain.user.dto.request;

import com.muscat.user.common.validation.ValidNickname;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "프로필 업데이트 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequestDto {

  @Schema(description = "닉네임(영문 · 숫자 · 한글 · _). 비우면 바꾸지 않음", example = "김투자자", required = false,
    minLength = 2, maxLength = 20)
  @ValidNickname
  private String nickname;
}
