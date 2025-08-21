package com.muscat.user.domain.service.impl;

import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.dto.ChangePasswordRequest;
import com.muscat.user.domain.dto.UpdateProfileRequest;
import com.muscat.user.domain.entity.EmailToken;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.mapper.UserMapper;
import com.muscat.user.domain.repository.EmailTokenRepository;
import com.muscat.user.domain.repository.UserRepository;
import com.muscat.user.domain.service.KeycloakService;
import com.muscat.user.domain.service.UserService;
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
  private final UserMapper userMapper;

  @Value("${app.mail.verification.expiry-hours}")
  private int expiryHours;

  @Override
  public User registerUser(String email, String password, String nickname) {
    if (userRepository.existsByEmail(email)) {
      log.warn("회원가입 실패 - 이미 존재하는 이메일: {}", email);
      throw new UserException(UserResponse.EMAIL_ALREADY_EXISTS);
    }

    try {
      // 1. Keycloak에 사용자 생성
      String keycloakId = keycloakService.createUser(email, password);

      // 2. 우리 DB에 사용자 생성 (Builder 패턴 사용)
      User user = userMapper.createLocalUser(
          email,
          passwordEncoder.encode(password),
          nickname,
          keycloakId
      );

      User savedUser = userRepository.save(user);

      // 3. 이메일 인증 토큰 생성 및 발송
      createAndSendVerificationToken(savedUser);

      log.info("회원가입 성공: {}", email);
      return savedUser;

    } catch (UserException e) {
      // 이미 적절한 UserException이므로 그대로 전파
      throw e;
    } catch (Exception e) {
      log.error("회원가입 처리 중 오류 발생: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR, "회원가입 처리 중 오류가 발생했습니다.");
    }
  }

  @Override
  public User verifyEmail(String token) {
    EmailToken emailToken = emailTokenRepository.findByToken(token)
        .orElseThrow(() -> {
          log.warn("유효하지 않은 이메일 인증 토큰: {}", token);
          return new UserException(UserResponse.EMAIL_TOKEN_INVALID);
        });

    if (emailToken.isExpired()) {
      log.warn("만료된 이메일 인증 토큰: {}", token);
      throw new UserException(UserResponse.EMAIL_TOKEN_EXPIRED);
    }

    User user = emailToken.getUser();
    user.setEmailVerified(true);

    // 사용된 토큰 삭제
    emailTokenRepository.delete(emailToken);

    User verifiedUser = userRepository.save(user);
    log.info("이메일 인증 완료: {}", user.getEmail());

    return verifiedUser;
  }

  @Override
  public void resendVerificationEmail(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("이메일 재발송 실패 - 사용자 없음: {}", email);
          return new UserException(UserResponse.USER_NOT_FOUND);
        });

    if (user.isEmailVerified()) {
      log.warn("이메일 재발송 실패 - 이미 인증됨: {}", email);
      throw new UserException(UserResponse.EMAIL_ALREADY_VERIFIED);
    }

    createAndSendVerificationToken(user);
    log.info("이메일 재발송 완료: {}", email);
  }

  @Override
  public String login(String email, String password) {
    // 1. DB에서 사용자 확인
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("로그인 실패 - 사용자 없음: {}", email);
          return new AuthenticationException(UserResponse.INVALID_CREDENTIALS);
        });

    // 2. 이메일 인증 확인
    if (!user.isEmailVerified()) {
      log.warn("로그인 실패 - 이메일 미인증: {}", email);
      throw new UserException(UserResponse.EMAIL_NOT_VERIFIED);
    }

    // 3. Keycloak으로 인증하고 JWT 받기
    try {
      String token = keycloakService.login(email, password);
      log.info("로그인 성공: {}", email);
      return token;
    } catch (Exception e) {
      log.warn("로그인 실패 - 인증 오류: {}", email);
      throw new AuthenticationException(UserResponse.INVALID_CREDENTIALS);
    }
  }

  @Override
  public User updateProfile(String email, UpdateProfileRequest request) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("프로필 수정 실패 - 사용자 없음: {}", email);
          return new UserException(UserResponse.USER_NOT_FOUND);
        });

    if (request.getNickname() != null) {
      // 닉네임 중복 체크
      if (userRepository.existsByNickname(request.getNickname()) &&
          !request.getNickname().equals(user.getNickname())) {
        log.warn("프로필 수정 실패 - 닉네임 중복: {}", request.getNickname());
        throw new UserException(UserResponse.NICKNAME_ALREADY_EXISTS);
      }
      user.setNickname(request.getNickname());
    }

    User updatedUser = userRepository.save(user);
    log.info("프로필 수정 완료: {}", email);

    return updatedUser;
  }

  @Override
  public void changePassword(String email, ChangePasswordRequest request) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("비밀번호 변경 실패 - 사용자 없음: {}", email);
          return new UserException(UserResponse.USER_NOT_FOUND);
        });

    // 현재 비밀번호 확인
    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
      log.warn("비밀번호 변경 실패 - 현재 비밀번호 불일치: {}", email);
      throw new UserException(UserResponse.INVALID_PASSWORD);
    }

    try {
      // Keycloak 비밀번호도 변경
      keycloakService.changePassword(user.getKeycloakId(), request.getNewPassword());

      // DB 비밀번호 변경
      user.setPassword(passwordEncoder.encode(request.getNewPassword()));
      userRepository.save(user);

      log.info("비밀번호 변경 완료: {}", email);
    } catch (Exception e) {
      log.error("비밀번호 변경 중 오류 발생: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR, "비밀번호 변경 중 오류가 발생했습니다.");
    }
  }

  @Override
  public void deleteAccount(String email, String password) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("계정 삭제 실패 - 사용자 없음: {}", email);
          return new UserException(UserResponse.USER_NOT_FOUND);
        });

    // 비밀번호 확인
    if (!passwordEncoder.matches(password, user.getPassword())) {
      log.warn("계정 삭제 실패 - 비밀번호 불일치: {}", email);
      throw new UserException(UserResponse.INVALID_PASSWORD);
    }

    try {
      // Keycloak 사용자 삭제
      keycloakService.deleteUser(user.getKeycloakId());

      // 관련 토큰들 삭제
      emailTokenRepository.deleteByUser(user);

      // 사용자 삭제
      userRepository.delete(user);

      log.info("계정 삭제 완료: {}", email);
    } catch (Exception e) {
      log.error("계정 삭제 중 오류 발생: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR, "계정 삭제 중 오류가 발생했습니다.");
    }
  }

  // 내부 메서드
  private void createAndSendVerificationToken(User user) {
    try {
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

      log.info("이메일 인증 토큰 생성 및 발송 완료: {}", user.getEmail());
    } catch (Exception e) {
      log.error("이메일 발송 실패: {}", e.getMessage(), e);
      throw new UserException(UserResponse.EMAIL_SEND_FAILED);
    }
  }
}