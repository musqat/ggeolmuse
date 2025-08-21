package com.muscat.user.domain.controller;

import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.common.responses.SocialResponse;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.dto.LoginRequest;
import com.muscat.user.domain.dto.RegisterRequest;
import com.muscat.user.domain.dto.ResendRequest;
import com.muscat.user.domain.dto.UserResponseDto;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.service.KeycloakService;
import com.muscat.user.domain.service.UserService;
import com.muscat.user.domain.service.SocialUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  private final UserService userService;
  private final SocialUserService socialUserService;

  @Value("${keycloak.auth-server-url}")
  private String keycloakUrl;

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.resource}")
  private String clientId;

  @Value("${app.frontend.url:http://localhost:3000}")
  private String frontendUrl;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
    User user = userService.registerUser(request.getEmail(), request.getPassword(), request.getNickname());
    log.info("회원가입 완료: {}", user.getEmail());

    return ResponseEntity.status(UserResponse.USER_CREATED.getHttpStatus())
        .body(ApiResponse.success(UserResponse.USER_CREATED));
  }

  @GetMapping("/verify-email")
  public ResponseEntity<String> verifyEmail(@RequestParam String token) {
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

  @PostMapping("/resend-verification")
  public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendRequest request) {
    userService.resendVerificationEmail(request.getEmail());
    log.info("이메일 재발송 완료: {}", request.getEmail());

    return ResponseEntity.ok(ApiResponse.success(UserResponse.EMAIL_VERIFIED, null));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody LoginRequest request) {
    String token = userService.login(request.getEmail(), request.getPassword());
    log.info("로그인 성공: {}", request.getEmail());

    return ResponseEntity.ok(ApiResponse.success(UserResponse.LOGIN_SUCCESS, token));
  }

  // ============= 소셜 로그인 엔드포인트 =============

  /**
   * Google 로그인 콜백 처리
   * Keycloak에서 Google 로그인 성공 후 리디렉션되는 엔드포인트
   */
  @GetMapping("/social/google/callback")
  public ResponseEntity<?> handleGoogleCallback(@RequestParam("code") String authorizationCode) {

    try {
      log.info("Google 콜백 처리 시작");

      // 1. Google 사용자 처리 및 DB 동기화 (서비스에서 모든 비즈니스 로직 처리)
      User user = socialUserService.processGoogleLogin(authorizationCode);

      // 2. 프론트엔드로 리디렉션 (성공 응답)
      String callbackUrl = String.format("%s/auth/callback?provider=google&email=%s",
          frontendUrl,
          URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8));

      log.info("Google 로그인 성공, 프론트엔드로 리디렉션: {}", user.getEmail());

      return ResponseEntity.status(302)
          .header("Location", callbackUrl)
          .build();

    } catch (Exception e) {
      log.error("Google 콜백 처리 실패: {}", e.getMessage());

      String errorUrl = String.format("%s/auth/error?message=%s",
          frontendUrl,
          URLEncoder.encode("Google 로그인 실패: " + e.getMessage(), StandardCharsets.UTF_8));

      return ResponseEntity.status(302)
          .header("Location", errorUrl)
          .build();
    }
  }

  /**
   * Google 로그인 URL 생성
   * 프론트엔드에서 Google 로그인 버튼 클릭 시 호출
   */
  @GetMapping("/social/google/login-url")
  public ResponseEntity<ApiResponse<Map<String, String>>> getGoogleLoginUrl(HttpServletRequest request) {
    // 현재 요청의 base URL 추출
    String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    String redirectUri = baseUrl + "/api/auth/social/google/callback";

    // Keycloak Google 로그인 URL 생성
    String authUrl = String.format(
        "%s/realms/%s/protocol/openid-connect/auth",
        keycloakUrl, realm);

    String loginUrl = String.format(
        "%s?client_id=%s&response_type=code&scope=%s&redirect_uri=%s&kc_idp_hint=google",
        authUrl,
        URLEncoder.encode(clientId, StandardCharsets.UTF_8),
        URLEncoder.encode("openid email profile", StandardCharsets.UTF_8),
        URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
    );

    Map<String, String> response = Map.of(
        "loginUrl", loginUrl,
        "provider", "google",
        "redirectUri", redirectUri
    );

    log.info("Google 로그인 URL 생성 완료");
    return ResponseEntity.ok(ApiResponse.success(SocialResponse.PROVIDERS_RETRIEVED, response));
  }

  /**
   * 수동 Google 사용자 동기화 (개발/테스트용)
   */
  @PostMapping("/social/google/sync")
  public ResponseEntity<ApiResponse<UserResponseDto>> syncGoogleUser(@RequestBody Map<String, Object> userInfo) {
    log.info("수동 Google 사용자 동기화 요청");

    User user = socialUserService.syncGoogleUser(userInfo);
    UserResponseDto userDto = UserResponseDto.from(user);

    log.info("Google 사용자 동기화 완료: {}", user.getEmail());
    return ResponseEntity.ok(ApiResponse.success(SocialResponse.GOOGLE_USER_SYNCED, userDto));
  }

  /**
   * 소셜 로그인 지원 제공자 목록 조회
   */
  @GetMapping("/social/providers")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getSocialProviders() {
    Map<String, Object> providers = Map.of(
        "providers", Map.of(
            "google", Map.of(
                "name", "Google",
                "loginUrl", "/api/auth/social/google/login-url",
                "enabled", true
            )
        )
    );

    log.info("소셜 로그인 제공자 목록 조회 완료");
    return ResponseEntity.ok(ApiResponse.success(SocialResponse.PROVIDERS_RETRIEVED, providers));
  }
}