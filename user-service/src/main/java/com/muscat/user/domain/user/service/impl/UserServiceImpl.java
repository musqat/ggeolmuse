package com.muscat.user.domain.user.service.impl;

import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.util.RateLimitService;
import com.muscat.user.config.mail.MailService;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.repository.EmailTokenRepository;
import com.muscat.user.domain.user.repository.PasswordResetTokenRepository;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
import com.muscat.user.infra.kafka.LoginEventProducer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final EmailTokenRepository emailTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final AccountRepository accountRepository;
  private final MailService mailService;
  private final KeycloakService keycloakService;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;
  private final RateLimitService rateLimitService;
  private final LoginEventProducer loginEventProducer;

  @Value("${app.mail.verification.expiry-hours:24}")
  private int expiryHours;

  @Value("${app.mail.password-reset.expiry-minutes:30}")
  private int passwordResetExpiryMinutes;

  @Override
  public User registerUser(String email, String password, String nickname) {
    if (userRepository.existsByEmail(email)) {
      log.warn("회원가입 실패 - 이미 존재하는 이메일: {}", email);
      throw new UserException(UserResponse.EMAIL_ALREADY_EXISTS);
    }

    String keycloakId;
    boolean isExistingKeycloakUser = false;

    try {
      keycloakId = keycloakService.createUser(email, password);
      log.info("Keycloak 사용자 생성 성공: {}", email);

    } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
      // Keycloak에 이미 사용자 존재 (Google OAuth 등)
      log.info("Keycloak에 이미 존재하는 사용자, 로컬 DB에만 저장: {}", email);
      isExistingKeycloakUser = true;

      // Keycloak에서 사용자 ID 조회
      keycloakId = keycloakService.findUserByEmail(email);

    } catch (UserException | KeycloakException e) {
      log.error("회원가입 실패: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("회원가입 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }

    try {
      User user = userMapper.createLocalUser(email, nickname, keycloakId);
      user.setPasswordHash(passwordEncoder.encode(password));

      // Google OAuth 사용자는 이미 이메일 인증됨
      if (isExistingKeycloakUser) {
        user.setEmailVerified(true);
      }

      User savedUser = userRepository.save(user);

      // 새로 생성한 Keycloak 사용자만 이메일 인증 필요
      if (!isExistingKeycloakUser) {
        createAndSendVerificationToken(savedUser);
      }

      log.info("회원가입 성공: {}", email);
      return savedUser;

    } catch (Exception e) {
      log.error("로컬 DB 저장 중 오류: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public User createUserFromKeycloak(String keycloakId, String email, String nickname) {
    // 이미 로컬 DB에 존재하는지 확인
    if (userRepository.existsByEmail(email)) {
      log.warn("Keycloak 동기화 실패 - 이미 존재하는 이메일: {}", email);
      throw new UserException(UserResponse.EMAIL_ALREADY_EXISTS);
    }

    try {
      User user = userMapper.createLocalUser(email, nickname, keycloakId);
      user.setEmailVerified(true); // OAuth 사용자는 이미 이메일 인증됨

      // OAuth 사용자는 비밀번호 없음 (null로 유지)

      User savedUser = userRepository.save(user);
      log.info("Keycloak OAuth 사용자 동기화 완료: {}", email);

      return savedUser;

    } catch (Exception e) {
      log.error("Keycloak 사용자 동기화 중 오류: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public String login(String email, String password) {
    User user;
    try {
      user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("로그인 실패 - 사용자 없음: {}", email);
          return new AuthenticationException(UserResponse.INVALID_CREDENTIALS);
        });
    } catch (AuthenticationException e) {
      // Kafka 이벤트 발행: 로그인 실패 (사용자 없음)
      loginEventProducer.publishLoginFailed(
        email, "ACCOUNT_NOT_FOUND", "계정이 존재하지 않습니다",
        "PASSWORD", null, null);
      throw e;
    }

    if (!user.isEmailVerified()) {
      log.warn("로그인 실패 - 이메일 미인증: {}", email);
      // Kafka 이벤트 발행: 로그인 실패 (이메일 미인증)
      loginEventProducer.publishLoginFailed(
        email, "EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다",
        "PASSWORD", null, null);
      throw new UserException(UserResponse.EMAIL_NOT_VERIFIED);
    }

    if (user.getPasswordHash() == null || !passwordEncoder.matches(password,
      user.getPasswordHash())) {
      log.warn("로그인 실패 - 비밀번호 불일치: {}", email);
      // Kafka 이벤트 발행: 로그인 실패 (비밀번호 불일치)
      loginEventProducer.publishLoginFailed(
        email, "INVALID_CREDENTIALS", "잘못된 이메일 또는 비밀번호입니다",
        "PASSWORD", null, null);
      throw new UserException(UserResponse.INVALID_CREDENTIALS);
    }

    String token;
    try {
      token = keycloakService.login(email, password);
    } catch (Exception e) {
      log.error("Keycloak 로그인 실패: {}", e.getMessage());
      // Kafka 이벤트 발행: 로그인 실패 (Keycloak 오류)
      loginEventProducer.publishLoginFailed(
        email, "KEYCLOAK_ERROR", "인증 서버 오류: " + e.getMessage(),
        "PASSWORD", null, null);
      throw e;
    }

    log.info("로그인 성공: {}", email);

    // Kafka 이벤트 발행: 로그인 성공
    loginEventProducer.publishLoginSuccess(
      user, "PASSWORD", null, null, true);

    return token;
  }

  @Override
  public User getProfile(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));
  }

  @Override
  public void deleteAccount(String email, String password) {
    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    // 로컬 비밀번호 검증
    if (user.getPasswordHash() == null || !passwordEncoder.matches(password,
      user.getPasswordHash())) {
      log.warn("계정 삭제 실패 - 비밀번호 불일치: {}", email);
      throw new UserException(UserResponse.INVALID_PASSWORD);
    }

    user.validateForDeletion();

    deleteUserRelatedData(user);

    if (user.getKeycloakId() != null) {
      try {
        keycloakService.deleteUser(user.getKeycloakId());
        log.info("Keycloak 사용자 삭제 완료: {}", user.getKeycloakId());
      } catch (Exception e) {
        log.warn("Keycloak 사용자 삭제 실패하지만 로컬 계정 삭제 진행: {}", e.getMessage());
      }
    }

    userRepository.delete(user);

    log.info("계정 삭제 완료: {}", email);
  }


  @Override
  public User updateProfile(String email, UpdateProfileRequestDto request) {
    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> {
        log.warn("프로필 수정 실패 - 사용자 없음: {}", email);
        return new UserException(UserResponse.USER_NOT_FOUND);
      });

    if (request.getNickname() != null) {
      if (userRepository.existsByNickname(request.getNickname()) &&
        !request.getNickname().equals(user.getNickname())) {
        log.warn("프로필 수정 실패 - 닉네임 중복: {}", request.getNickname());
        throw new UserException(UserResponse.NICKNAME_ALREADY_EXISTS);
      }
      user.setNickname(request.getNickname());
    }

    User updatedUser = userRepository.save(user);
    log.debug("프로필 수정 완료: {}", email);

    return updatedUser;
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

    emailTokenRepository.delete(emailToken);

    User verifiedUser = userRepository.save(user);
    log.debug("이메일 인증 완료: {}", user.getEmail());

    return verifiedUser;
  }

  @Override
  public void resendVerificationEmail(String email) {
    // Rate limiting 체크
    if (!rateLimitService.tryAcquire(email)) {
      long remainingSeconds = rateLimitService.getRemainingWaitSeconds(email);
      log.warn("인증 이메일 재발송 Rate limit 초과: email={}, 남은 대기 시간: {}초", email, remainingSeconds);
      throw new UserException(UserResponse.RATE_LIMIT_EXCEEDED);
    }

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
    log.debug("이메일 재발송 완료: {}", email);
  }

  @Override
  public void requestPasswordReset(String email) {
    // Rate limiting 체크
    if (!rateLimitService.tryAcquire(email)) {
      long remainingSeconds = rateLimitService.getRemainingWaitSeconds(email);
      log.warn("비밀번호 재설정 요청 Rate limit 초과: email={}, 남은 대기 시간: {}초", email, remainingSeconds);
      throw new UserException(UserResponse.RATE_LIMIT_EXCEEDED);
    }

    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> {
        log.warn("비밀번호 재설정 요청 실패 - 사용자 없음: {}", email);
        return new UserException(UserResponse.USER_NOT_FOUND);
      });

    // 이메일 미인증 사용자는 비밀번호 재설정 불가
    if (!user.isEmailVerified()) {
      log.warn("비밀번호 재설정 요청 실패 - 이메일 미인증: {}", email);
      throw new UserException(UserResponse.EMAIL_NOT_VERIFIED);
    }

    createAndSendPasswordResetToken(user);
    log.info("비밀번호 재설정 이메일 발송 완료: {}", email);
  }

  @Override
  public void resetPassword(String token, String newPassword) {
    PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
      .orElseThrow(() -> {
        log.warn("유효하지 않은 비밀번호 재설정 토큰: {}", token);
        return new UserException(UserResponse.PASSWORD_RESET_TOKEN_INVALID);
      });

    if (!resetToken.isValid()) {
      log.warn("만료되었거나 이미 사용된 비밀번호 재설정 토큰: {}", token);
      throw new UserException(UserResponse.PASSWORD_RESET_TOKEN_EXPIRED);
    }

    User user = resetToken.getUser();

    // Keycloak에서 비밀번호 변경
    try {
      keycloakService.resetPassword(user.getKeycloakId(), newPassword);
      log.info("Keycloak 비밀번호 재설정 성공: {}", user.getEmail());
    } catch (Exception e) {
      log.error("Keycloak 비밀번호 재설정 실패: {}", user.getEmail(), e);
      throw new UserException(UserResponse.PASSWORD_RESET_FAILED);
    }

    // 로컬 DB 비밀번호 해시 업데이트
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    // 토큰 사용 처리 및 삭제
    resetToken.markAsUsed();
    passwordResetTokenRepository.save(resetToken);

    log.info("비밀번호 재설정 완료: {}", user.getEmail());
  }

  // ========== 내부 Methods ==========

  // 사용자 관련 데이터 정리
  private void deleteUserRelatedData(User user) {
    emailTokenRepository.deleteByUser(user);

    List<Account> accounts = accountRepository.findByUserIdWithUser(user.getId());
    validateAccountsForDeletion(accounts);

    log.debug("사용자 관련 데이터 정리 완료: userId={}, 계좌수={}", user.getId(), accounts.size());
  }

  // 계좌들 삭제 가능 여부 검증
  private void validateAccountsForDeletion(List<Account> accounts) {
    boolean hasBalance = accounts.stream()
      .anyMatch(account -> account.getBalanceKrw().compareTo(BigDecimal.ZERO) > 0 ||
        account.getBalanceUsd().compareTo(BigDecimal.ZERO) > 0);

    if (hasBalance) {
      throw new UserException(UserResponse.ACCOUNT_DELETION_BLOCKED);
    }
  }

  // 이메일 인증 토큰 생성 및 발송
  private void createAndSendVerificationToken(User user) {
    emailTokenRepository.deleteByUser(user);

    EmailToken token = new EmailToken();
    token.setToken(UUID.randomUUID().toString());
    token.setUser(user);
    token.setExpiryDate(LocalDateTime.now().plusHours(expiryHours));

    emailTokenRepository.save(token);

    try {
      mailService.sendVerificationEmail(user.getEmail(), token.getToken());
      log.debug("이메일 인증 메일 발송 성공: {}", user.getEmail());
    } catch (Exception e) {
      log.warn("이메일 발송 실패했지만 사용자 등록은 성공: {}", user.getEmail(), e);
    }
  }

  // 비밀번호 재설정 토큰 생성 및 발송
  private void createAndSendPasswordResetToken(User user) {
    // 기존 토큰 삭제
    passwordResetTokenRepository.deleteByUser(user);

    // 새 토큰 생성
    PasswordResetToken token = new PasswordResetToken();
    token.setToken(UUID.randomUUID().toString());
    token.setUser(user);
    token.setExpiryDate(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes));

    passwordResetTokenRepository.save(token);

    try {
      mailService.sendPasswordResetEmail(user.getEmail(), token.getToken());
      log.debug("비밀번호 재설정 메일 발송 성공: {}", user.getEmail());
    } catch (Exception e) {
      log.warn("비밀번호 재설정 이메일 발송 실패: {}", user.getEmail(), e);
    }
  }

  // ==================== ADMIN API ====================

  @Override
  @Transactional(readOnly = true)
  public Page<User> getAllUsers(Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public User findById(Long userId) {
    return userRepository.findById(userId)
      .orElseThrow(() -> {
        log.warn("사용자 조회 실패 - ID 없음: {}", userId);
        return new UserException(UserResponse.USER_NOT_FOUND);
      });
  }

  @Override
  public User updateUserRole(Long userId, UserRole role) {
    User user = findById(userId);

    log.info("사용자 역할 변경: userId={}, oldRole={}, newRole={}",
      userId, user.getRole(), role);

    user.setRole(role);
    return userRepository.save(user);
  }

  @Override
  public User updateUserEnabled(Long userId, boolean enabled) {
    User user = findById(userId);

    log.info("사용자 활성화 상태 변경: userId={}, oldEnabled={}, newEnabled={}",
      userId, user.isEnabled(), enabled);

    user.setEnabled(enabled);
    return userRepository.save(user);
  }

  @Override
  @Transactional(readOnly = true)
  public long countTotalUsers() {
    return userRepository.count();
  }

  @Override
  @Transactional(readOnly = true)
  public long countActiveUsers() {
    return userRepository.countByEnabled(true);
  }

  @Override
  @Transactional(readOnly = true)
  public long countAdminUsers() {
    return userRepository.countByRole(UserRole.ADMIN);
  }

  @Override
  public User adminUpdateNickname(Long userId, String nickname) {
    User user = findById(userId);

    log.info("Admin이 사용자 닉네임 강제 변경: userId={}, oldNickname={}, newNickname={}",
      userId, user.getNickname(), nickname);

    user.setNickname(nickname);
    return userRepository.save(user);
  }

  @Override
  public void adminUpdatePassword(Long userId, String newPassword) {
    User user = findById(userId);

    log.info("Admin이 사용자 비밀번호 강제 변경: userId={}, email={}",
      userId, user.getEmail());

    // Keycloak 비밀번호 변경
    try {
      keycloakService.resetPassword(user.getKeycloakId(), newPassword);
      log.info("Keycloak 비밀번호 재설정 성공: {}", user.getEmail());
    } catch (Exception e) {
      log.error("Keycloak 비밀번호 재설정 실패: {}", user.getEmail(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }

    // 로컬 DB 비밀번호 해시 업데이트
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    log.info("사용자 비밀번호 강제 변경 완료: {}", user.getEmail());
  }

  @Override
  public User adminVerifyEmail(Long userId) {
    User user = findById(userId);

    log.info("Admin이 사용자 이메일 강제 인증: userId={}, email={}",
      userId, user.getEmail());

    user.setEmailVerified(true);

    // 이메일 인증 토큰 삭제 (있다면)
    emailTokenRepository.deleteByUser(user);

    User verifiedUser = userRepository.save(user);
    log.info("사용자 이메일 강제 인증 완료: {}", user.getEmail());

    return verifiedUser;
  }

  @Override
  public void adminDeleteUser(Long userId) {
    User user = findById(userId);

    log.info("Admin이 사용자 강제 탈퇴 처리: userId={}, email={}",
      userId, user.getEmail());

    // 사용자 관련 데이터 정리 (계좌 잔액 체크 없이 강제 삭제)
    emailTokenRepository.deleteByUser(user);
    passwordResetTokenRepository.deleteByUser(user);

    // 계좌도 함께 삭제 (잔액 체크 없이)
    List<Account> accounts = accountRepository.findByUserIdWithUser(userId);
    accountRepository.deleteAll(accounts);

    log.info("사용자 관련 데이터 정리 완료: userId={}, 계좌수={}", userId, accounts.size());

    // Keycloak 사용자 삭제
    if (user.getKeycloakId() != null) {
      try {
        keycloakService.deleteUser(user.getKeycloakId());
        log.info("Keycloak 사용자 삭제 완료: {}", user.getKeycloakId());
      } catch (Exception e) {
        log.warn("Keycloak 사용자 삭제 실패하지만 로컬 계정 삭제 진행: {}", e.getMessage());
      }
    }

    // 로컬 DB에서 사용자 삭제
    userRepository.delete(user);

    log.info("사용자 강제 탈퇴 완료: {}", user.getEmail());
  }

}
