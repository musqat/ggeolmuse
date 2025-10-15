package com.muscat.user.domain.user.controller;

import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.user.dto.request.LoginRequestDto;
import com.muscat.user.domain.user.dto.request.RegisterRequestDto;
import com.muscat.user.domain.user.dto.request.ResendRequestDto;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.service.SocialUserService;
import com.muscat.user.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
  private final SocialUserService socialUserService;

  @Value("${keycloak.auth-server-url}")
  private String keycloakUrl;

  @Value("${keycloak.public-url:${keycloak.auth-server-url}}")
  private String keycloakPublicUrl;

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.resource}")
  private String clientId;

  @Value("${app.frontend.url:http://localhost:3000}")
  private String frontendUrl;

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
      summary = "사용자 로그인",
      description = "이메일과 비밀번호로 로그인하여 JWT 토큰을 발급받습니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "로그인 성공",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(
                  value = "\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGdnZW9sbXVzZS5jb20iLCJpYXQiOjE2MzA0NzUyMDAsImV4cCI6MTYzMDU2MTYwMH0.xyz123\""
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
  public ResponseEntity<String> login(
      @Parameter(description = "로그인 정보", required = true)
      @Valid @RequestBody LoginRequestDto request) {
    String token = userService.login(request.getEmail(), request.getPassword());
    log.info("로그인 성공: {}", request.getEmail());

    return ResponseEntity.ok(token);
  }

  // ============= 소셜 로그인 =============

  @Operation(
      summary = "Google 로그인 콜백 처리",
      description = "Keycloak를 통한 Google 소셜 로그인 성공 후 콜백을 처리하고 프론트엔드로 리디렉션합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "302",
          description = "Google 로그인 성공, 프론트엔드로 리디렉션",
          content = @Content(
              mediaType = "text/html",
              examples = @ExampleObject(
                  value = "Location: http://localhost:3000/auth/callback?provider=google&email=user@example.com"
              )
          )
      ),
      @ApiResponse(
          responseCode = "302",
          description = "Google 로그인 실패, 에러 페이지로 리디렉션",
          content = @Content(
              mediaType = "text/html",
              examples = @ExampleObject(
                  value = "Location: http://localhost:3000/auth/error?message=Google+로그인+실패"
              )
          )
      )
  })
  @GetMapping("/social/google/callback")
  public ResponseEntity<?> handleGoogleCallback(
      @Parameter(description = "Google OAuth 인증 코드", required = true, example = "4/0AdQt8qhexample...")
      @RequestParam("code") String authorizationCode) {

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

  @Operation(
      summary = "Google 로그인 URL 생성",
      description = "Keycloak를 통한 Google 소셜 로그인 URL을 생성하여 반환합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Google 로그인 URL 생성 성공",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(
                  value = """
                      {
                        "loginUrl": "https://keycloak.example.com/realms/ggeolmuse/protocol/openid-connect/auth?client_id=ggeolmuse-frontend&response_type=code&scope=openid+email+profile&redirect_uri=http://localhost:8080/api/auth/social/google/callback&kc_idp_hint=google",
                        "provider": "google",
                        "redirectUri": "http://localhost:8080/api/auth/social/google/callback"
                      }
                      """
              )
          )
      )
  })
  @GetMapping("/social/google/login-url")
  public ResponseEntity<Map<String, String>> getGoogleLoginUrl(
      @Parameter(description = "HTTP 요청 객체 (서버 정보 추출용)", hidden = true)
      HttpServletRequest request) {
    // 현재 요청의 base URL 추출 (포트 처리 개선)
    String baseUrl = request.getScheme() + "://" + request.getServerName();
    int port = request.getServerPort();
    if ((port != 80 && "http".equals(request.getScheme())) ||
        (port != 443 && "https".equals(request.getScheme()))) {
      baseUrl += ":" + port;
    }
    String redirectUri = baseUrl + "/api/auth/social/google/callback";

    // Keycloak Google 로그인 URL 생성 (browser-facing public URL 사용)
    String authUrl = String.format(
        "%s/realms/%s/protocol/openid-connect/auth",
        keycloakPublicUrl, realm);

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
    return ResponseEntity.ok(response);
  }


}