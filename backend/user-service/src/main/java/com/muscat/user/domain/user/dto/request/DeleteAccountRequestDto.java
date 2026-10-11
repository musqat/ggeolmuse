package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "계정 삭제 요청 정보")
@Getter
@Setter
@NoArgsConstructor
public class DeleteAccountRequestDto {
  // 비밀번호로 가입한 사용자만 보낸다. 구글 사용자는 비우고, 서버가 토큰의 로그인 시각으로 본인을 확인한다
  @Schema(description = "비밀번호(비밀번호로 가입한 사용자만)", example = "password123")
  private String password;
}
