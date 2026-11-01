package com.muscat.user.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 구글 로그인 콜백이 받은 인가 코드와 브라우저가 만든 PKCE code_verifier
@Schema(description = "구글 로그인 인가 코드 교환 요청")
public record GoogleTokenRequestDto(
  @Schema(description = "Keycloak 이 콜백으로 준 인가 코드", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "인가 코드는 필수입니다")
  String code,

  @Schema(description = "PKCE code_verifier", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "code_verifier 는 필수입니다")
  String codeVerifier,

  @Schema(description = "인가 요청 때 쓴 redirect_uri", example = "https://ggeolmuse.com/oauth/callback", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "redirect_uri 는 필수입니다")
  String redirectUri
) {

}
