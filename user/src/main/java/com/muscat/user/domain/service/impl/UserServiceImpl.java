package com.muscat.user.domain.service.impl;

import com.muscat.user.domain.dto.ChangePasswordRequest;
import com.muscat.user.domain.dto.UpdateProfileRequest;
import com.muscat.user.domain.entity.EmailToken;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.repository.EmailTokenRepository;
import com.muscat.user.domain.repository.UserRepository;
import com.muscat.user.domain.service.UserService;
import com.muscat.user.enums.AuthProvider;
import com.muscat.user.keycloak.KeycloakService;
import com.muscat.user.mail.MailService;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final EmailTokenRepository emailTokenRepository;
  private final MailService mailService;
  private final PasswordEncoder passwordEncoder;
  private final KeycloakService keycloakService;

  @Value("${app.mail.verification.expiry-hours}")
  private int expiryHours;

  @Override
  public User registerUser(String email, String password, String nickname) {
    if (userRepository.existsByEmail(email)) {
      throw new RuntimeException("Email already exists");
    }

    try {
      // 1. Keycloak에 사용자 생성
      String keycloakId = keycloakService.createUser(email, password);

      // 2. 우리 DB에 사용자 생성
      User user = new User();
      user.setEmail(email);
      user.setPassword(passwordEncoder.encode(password));
      user.setNickname(nickname);
      user.setProvider(AuthProvider.LOCAL);
      user.setEmailVerified(false);
      user.setKeycloakId(keycloakId); // Keycloak ID 저장

      User savedUser = userRepository.save(user);

      // 3. 이메일 인증 토큰 생성 및 발송
      createAndSendVerificationToken(savedUser);

      return savedUser;

    } catch (Exception e) {
      log.error("Failed to register user: {}", e.getMessage());
      throw new RuntimeException("Failed to register user: " + e.getMessage());
    }
  }


  @Override
  public User verifyEmail(String token) {
    EmailToken emailToken = emailTokenRepository.findByToken(token)
        .orElseThrow(() -> new RuntimeException("Invalid token"));

    if (emailToken.isExpired()) {
      throw new RuntimeException("Token expired");
    }

    User user = emailToken.getUser();
    user.setEmailVerified(true);

    // 사용된 토큰 삭제
    emailTokenRepository.delete(emailToken);

    return userRepository.save(user);
  }

  @Override
  public void resendVerificationEmail(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    if (user.isEmailVerified()) {
      throw new RuntimeException("Email already verified");
    }

    createAndSendVerificationToken(user);
  }

  @Override
  public String login(String email, String password) {
    // 1. DB에서 사용자 확인
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // 2. 이메일 인증 확인
    if (!user.isEmailVerified()) {
      throw new RuntimeException("Email not verified");
    }

    // 3. Keycloak으로 인증하고 JWT 받기
    return keycloakService.login(email, password);
  }

  @Override
  public User updateProfile(String email, UpdateProfileRequest request) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    if (request.getNickname() != null) {
      user.setNickname(request.getNickname());
    }

    return userRepository.save(user);
  }

  @Override
  public void changePassword(String email, ChangePasswordRequest request) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // 현재 비밀번호 확인
    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
      throw new RuntimeException("Current password is incorrect");
    }

    // Keycloak 비밀번호도 변경
    keycloakService.changePassword(user.getKeycloakId(), request.getNewPassword());

    // DB 비밀번호 변경
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
  }

  @Override
  public void deleteAccount(String email, String password) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // 비밀번호 확인
    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new RuntimeException("Password is incorrect");
    }

    // Keycloak 사용자 삭제
    keycloakService.deleteUser(user.getKeycloakId());

    // 관련 토큰들 삭제
    emailTokenRepository.deleteByUser(user);

    // 사용자 삭제
    userRepository.delete(user);
  }

  // 내부 메서드

  private void createAndSendVerificationToken(User user) {
    // 기존 토큰 삭제
    emailTokenRepository.deleteByUser(user);

    // 새 토큰 생성
    EmailToken token = new EmailToken();
    token.setToken(UUID.randomUUID().toString());
    token.setUser(user);
    token.setExpiryDate(LocalDateTime.now().plusHours(expiryHours));

    emailTokenRepository.save(token);

    // 이메일 발송
    mailService.sendVerificationEmail(user.getEmail(), token.getToken());
  }

}