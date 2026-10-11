package com.muscat.user.domain.user.controller;

import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.user.dto.request.ForgotPasswordRequestDto;
import com.muscat.user.domain.user.dto.request.LoginRequestDto;
import com.muscat.user.domain.user.dto.request.GoogleTokenRequestDto;
import com.muscat.user.domain.user.dto.request.RegisterRequestDto;
import com.muscat.user.domain.user.dto.request.ResendRequestDto;
import com.muscat.user.domain.user.dto.request.ResetPasswordRequestDto;
import com.muscat.user.common.enums.responses.KeycloakResponse;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.domain.user.dto.response.AccessTokenResponseDto;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "인증 관리", description = "사용자 회원가입, 로그인, 이메일 인증 관련 API")
public class AuthController {

  private final UserService userService;
  private final RefreshTokenCookie refreshTokenCookie;

  @Operation(
    summary = "사용자 회원가입",
    description = "새로운 사용자 계정을 생성합니다. 가입 후 이메일 인증이 필요합니다."
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "201",
      description = "회원가입 성공",
      content = @Content(mediaType = "application/json")
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 요청 데이터",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class),
        examples = @ExampleObject(
          value = """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "이메일 형식이 올바르지 않습니다",
              "instance": "/api/auth/register"
            }
            """
        )
      )
    ),
    @ApiResponse(
      responseCode = "409",
      description = "이미 존재하는 사용자",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class),
        examples = @ExampleObject(
          value = """
            {
              "type": "about:blank",
              "title": "Conflict",
              "status": 409,
              "detail": "이미 가입된 이메일입니다",
              "instance": "/api/auth/register"
            }
            """
        )
      )
    )
  })
  @PostMapping("/register")
  public ResponseEntity<Void> register(
    @Parameter(description = "회원가입 정보", required = true)
    @Valid @RequestBody RegisterRequestDto request) {
    User user = userService.registerUser(request.getEmail(), request.getPassword(),
      request.getNickname());
    log.info("회원가입 완료: {}", user.getEmail());

    return ResponseEntity.status(201).build();
  }

  @Operation(
    summary = "이메일 인증",
    description = "회원가입 후 발송된 이메일의 인증 토큰으로 계정을 활성화합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "이메일 인증 성공",
      content = @Content(
        mediaType = "text/html",
        examples = @ExampleObject(
          value = "<html><body><h2>이메일 인증이 완료되었습니다</h2></body></html>"
        )
      )
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 토큰 또는 만료된 토큰",
      content = @Content(
        mediaType = "text/html",
        examples = @ExampleObject(
          value = "<html><body><h2>인증에 실패했습니다</h2></body></html>"
        )
      )
    )
  })
  @GetMapping("/verify-email")
  public ResponseEntity<String> verifyEmail(
    @Parameter(description = "이메일 인증 토큰", required = true, example = "eyJhbGciOiJIUzI1NiJ9...")
    @RequestParam String token) {
    try {
      User user = userService.verifyEmail(token);
      log.info("이메일 인증 완료: {}", user.getEmail());

      String successHtml = """
        <html>
        <head><meta charset="UTF-8"><title>인증 완료</title></head>
        <body style="font-family: Arial; text-align: center; margin-top: 100px;">
          <h2>이메일 인증이 완료되었습니다</h2>
          <p>계정이 활성화되었습니다.</p>
        </body>
        </html>
        """;

      return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body(successHtml);

    } catch (UserException e) {
      log.warn("이메일 인증 실패: {}", e.getMessage());

      String errorHtml = """
        <html>
        <head><meta charset="UTF-8"><title>인증 실패</title></head>
        <body style="font-family: Arial; text-align: center; margin-top: 100px;">
          <h2>인증에 실패했습니다</h2>
          <p>%s</p>
        </body>
        </html>
        """.formatted(e.getMessage());

      return ResponseEntity.badRequest()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body(errorHtml);
    }
  }

  @Operation(
    summary = "이메일 인증 재발송",
    description = "이메일 인증을 받지 못한 경우 인증 이메일을 다시 발송합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "인증 이메일 재발송 성공"
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 이메일 또는 이미 인증된 계정",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "존재하지 않는 사용자",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @PostMapping("/resend-verification")
  public ResponseEntity<Void> resendVerification(
    @Parameter(description = "인증 이메일 재발송 요청", required = true)
    @Valid @RequestBody ResendRequestDto request) {
    userService.resendVerificationEmail(request.getEmail());
    log.info("이메일 재발송 완료: {}", request.getEmail());

    return ResponseEntity.ok().build();
  }

  @Operation(
    summary = "비밀번호 재설정 요청",
    description = "이메일을 입력하면 비밀번호 재설정 링크가 포함된 이메일이 발송됩니다."
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "비밀번호 재설정 이메일 발송 성공"
    ),
    @ApiResponse(
      responseCode = "404",
      description = "존재하지 않는 사용자",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    ),
    @ApiResponse(
      responseCode = "429",
      description = "요청 횟수 초과 (1분에 1번 제한)",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgotPassword(
    @Parameter(description = "비밀번호 재설정 요청", required = true)
    @Valid @RequestBody ForgotPasswordRequestDto request) {
    userService.requestPasswordReset(request.getEmail());
    log.info("비밀번호 재설정 이메일 발송: {}", request.getEmail());

    return ResponseEntity.ok().build();
  }

  @Operation(
    summary = "비밀번호 재설정",
    description = "이메일로 받은 토큰을 사용하여 새로운 비밀번호로 재설정합니다."
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "비밀번호 재설정 성공"
    ),
    @ApiResponse(
      responseCode = "400",
      description = "유효하지 않거나 만료된 토큰",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    ),
    @ApiResponse(
      responseCode = "500",
      description = "비밀번호 재설정 실패",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(
    @Parameter(description = "비밀번호 재설정 정보", required = true)
    @Valid @RequestBody ResetPasswordRequestDto request) {
    userService.resetPassword(request.getToken(), request.getNewPassword());
    log.info("비밀번호 재설정 완료");

    return ResponseEntity.ok().build();
  }

  @Operation(
    summary = "사용자 로그인",
    description = "이메일과 비밀번호로 로그인합니다. access token 은 본문으로, refresh token 은 httpOnly 쿠키(refresh_token)로 줍니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "로그인 성공",
      content = @Content(
        mediaType = "application/json",
        examples = @ExampleObject(
          value = "{\"accessToken\": \"eyJhbGciOiJSUzI1NiJ9...\", \"expiresIn\": 3600}"
        )
      )
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 이메일 또는 비밀번호",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    ),
    @ApiResponse(
      responseCode = "403",
      description = "이메일 인증이 완료되지 않은 계정",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @PostMapping("/login")
  public ResponseEntity<AccessTokenResponseDto> login(
    @Parameter(description = "로그인 정보", required = true)
    @Valid @RequestBody LoginRequestDto request) {
    TokenResponseDto tokens = userService.login(request.getEmail(), request.getPassword());
    log.info("로그인 성공: {}", request.getEmail());

    return withRefreshCookie(tokens);
  }

  @Operation(
    summary = "토큰 갱신",
    description = "refresh_token 쿠키로 새 access token 을 받습니다. 쿠키도 새 refresh token 으로 바뀝니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "토큰 갱신 성공",
      content = @Content(
        mediaType = "application/json",
        examples = @ExampleObject(
          value = "{\"accessToken\": \"eyJhbGciOiJSUzI1NiJ9...\", \"expiresIn\": 3600}"
        )
      )
    ),
    @ApiResponse(
      responseCode = "401",
      description = "유효하지 않거나 만료된 Refresh token",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @PostMapping("/refresh")
  public ResponseEntity<AccessTokenResponseDto> refreshToken(
    @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new KeycloakException(KeycloakResponse.TOKEN_REFRESH_FAILED);
    }
    TokenResponseDto tokens = userService.refreshToken(refreshToken);
    log.info("토큰 갱신 성공");

    return withRefreshCookie(tokens);
  }

  @Operation(
    summary = "로그아웃",
    description = "refresh_token 쿠키가 속한 Keycloak 세션을 끊고 쿠키를 지웁니다. 쿠키가 없거나 이미 끝난 세션이어도 204 입니다"
  )
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "로그아웃 완료")
  })
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
    @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      try {
        userService.logout(refreshToken);
      } catch (KeycloakException e) {
        // 세션 종료가 실패해도 쿠키는 지운다. 원인은 KeycloakServiceImpl 이 로그로 남긴다
        log.error("로그아웃 중 Keycloak 세션 종료 실패: {}", e.getMessage());
      }
    }
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear()).build();
  }

  @Operation(
    summary = "구글 로그인 코드 교환",
    description = "구글 로그인(Keycloak, PKCE) 콜백의 인가 코드를 토큰으로 바꿉니다. access token 은 본문, refresh token 은 httpOnly 쿠키입니다"
  )
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "교환 성공"),
    @ApiResponse(responseCode = "400", description = "코드 만료 · 재사용 · verifier 불일치",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping("/social/google/token")
  public ResponseEntity<AccessTokenResponseDto> exchangeGoogleCode(
    @Valid @RequestBody GoogleTokenRequestDto request) {
    TokenResponseDto tokens = userService.exchangeGoogleCode(request.code(), request.codeVerifier(), request.redirectUri());
    return withRefreshCookie(tokens);
  }

  // access token 은 본문, refresh token 은 httpOnly 쿠키
  private ResponseEntity<AccessTokenResponseDto> withRefreshCookie(TokenResponseDto tokens) {
    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(tokens))
      .body(AccessTokenResponseDto.from(tokens));
  }

}
