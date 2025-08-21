package com.muscat.user.controller;

import com.muscat.user.domain.dto.LoginRequest;
import com.muscat.user.domain.dto.RegisterRequest;
import com.muscat.user.domain.dto.ResendRequest;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  @PostMapping("/register")
  public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
    User user = userService.registerUser(request.getEmail(), request.getPassword(), request.getNickname());
    return ResponseEntity.ok("회원가입이 완료되었습니다. 이메일을 확인해주세요.");
  }

  @GetMapping("/verify-email")
  public ResponseEntity<String> verifyEmail(@RequestParam String token) {
    User user = userService.verifyEmail(token);
    return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<String> resendVerification(@Valid @RequestBody ResendRequest request) {
    userService.resendVerificationEmail(request.getEmail());
    return ResponseEntity.ok("인증 이메일을 재발송했습니다.");
  }

  @PostMapping("/login")
  public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
    try {
      String token = userService.login(request.getEmail(), request.getPassword());
      return ResponseEntity.ok(token);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body("Login failed: " + e.getMessage());
    }
  }

}