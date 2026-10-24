package com.muscat.user.domain.user.service.impl;

import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exception.AuthenticationException;
import com.muscat.user.common.exception.KeycloakException;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.common.util.RateLimitService;
import com.muscat.user.common.validation.NicknameRule;
import com.muscat.user.config.mail.MailService;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.repository.EmailTokenRepository;
import com.muscat.user.domain.user.repository.PasswordResetTokenRepository;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.LoginEventProducer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
  private final AccountEventProducer accountEventProducer;

  // 비밀번호 없는 사용자(구글)는 이 시간 안에 로그인한 토큰이어야 탈퇴할 수 있다. 훔친 access token 만으로는 못 지운다
  private static final Duration REAUTH_WINDOW = Duration.ofMinutes(5);
  // 관리자 권한을 정하는 Keycloak realm role
  private static final String ADMIN_REALM_ROLE = "admin";

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
    if (userRepository.existsByNickname(nickname)) {
      log.warn("회원가입 실패 - 이미 쓰는 닉네임: {}", nickname);
      throw new UserException(UserResponse.NICKNAME_ALREADY_EXISTS);
    }

    String keycloakId;

    try {
      keycloakId = keycloakService.createUser(email, password);
      log.info("Keycloak 사용자 생성 성공: {}", email);

    } catch (org.springframework.web.client.HttpClientErrorException.Conflict e) {
      // Keycloak 에 이미 있다(구글로 가입하고 로컬 DB 에는 없는 경우 등). 이메일 확인 없이 그 계정에
      // 연결하면 남의 이메일로 가입해 인증된 계정을 얻을 수 있어, 로컬 중복과 같이 이미 가입된 이메일로 막는다
      log.warn("회원가입 실패 - Keycloak 에 이미 있는 이메일: {}", email);
      throw new UserException(UserResponse.EMAIL_ALREADY_EXISTS);

    } catch (UserException | KeycloakException e) {
      log.error("회원가입 실패: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("회원가입 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }

    try {
      User user = userMapper.createLocalUser(email, nickname, keycloakId);
      user.changePassword(passwordEncoder.encode(password));

      User savedUser = userRepository.save(user);
      createAndSendVerificationToken(savedUser);

      log.info("회원가입 성공: {}", email);
      return savedUser;

    } catch (Exception e) {
      log.error("로컬 DB 저장 중 오류: {}", e.getMessage(), e);
      deleteKeycloakUserQuietly(keycloakId);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }
  }

  // 구글 이름을 닉네임 규칙에 맞게 다듬은 값. 규칙에 못 맞추거나 다른 사용자가 쓰면 null
  private String availableNickname(String name) {
    String cleaned = NicknameRule.clean(name);
    if (cleaned == null || userRepository.existsByNickname(cleaned)) {
      log.info("구글 이름을 닉네임으로 못 씀 - 비워 두고 만듦");
      return null;
    }
    return cleaned;
  }

  // DB 저장이 실패한 가입의 Keycloak 계정 삭제(삭제도 실패하면 로그만)
  private void deleteKeycloakUserQuietly(String keycloakId) {
    try {
      keycloakService.deleteUser(keycloakId);
    } catch (Exception e) {
      log.error("가입 되돌리기 실패 - Keycloak 계정이 남음: keycloakId={}", keycloakId, e);
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
      User user = userMapper.createLocalUser(email, availableNickname(nickname), keycloakId);
      user.verifyEmail(); // OAuth 사용자는 이미 이메일 인증됨

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
  public TokenResponseDto login(String email, String password) {
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

    // 비밀번호를 이메일 인증 체크보다 먼저 본다.
    // 인증 체크가 앞서면, 비번을 몰라도 미인증(400)과 없는 계정(401)의 응답이 갈려
    // 가입 여부가 드러난다. 비번을 통과한 사람에게만 미인증 안내를 주도록 순서를 둔다.
    if (user.getPasswordHash() == null || !passwordEncoder.matches(password,
      user.getPasswordHash())) {
      log.warn("로그인 실패 - 비밀번호 불일치: {}", email);
      // Kafka 이벤트 발행: 로그인 실패 (비밀번호 불일치)
      loginEventProducer.publishLoginFailed(
        email, "INVALID_CREDENTIALS", "잘못된 이메일 또는 비밀번호입니다",
        "PASSWORD", null, null);
      throw new UserException(UserResponse.INVALID_CREDENTIALS);
    }

    if (!user.isEmailVerified()) {
      log.warn("로그인 실패 - 이메일 미인증: {}", email);
      // Kafka 이벤트 발행: 로그인 실패 (이메일 미인증)
      loginEventProducer.publishLoginFailed(
        email, "EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다",
        "PASSWORD", null, null);
      throw new UserException(UserResponse.EMAIL_NOT_VERIFIED);
    }

    if (!user.isEnabled()) {
      log.warn("로그인 실패 - 비활성 계정: {}", email);
      loginEventProducer.publishLoginFailed(
        email, "ACCOUNT_DISABLED", "비활성화된 계정입니다",
        "PASSWORD", null, null);
      throw new UserException(UserResponse.ACCOUNT_DISABLED);
    }

    TokenResponseDto token;
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
  public TokenResponseDto refreshToken(String refreshToken) {
    log.info("토큰 갱신 요청");

    try {
      TokenResponseDto tokens = keycloakService.refreshToken(refreshToken);
      log.info("토큰 갱신 성공");
      return tokens;
    } catch (Exception e) {
      log.error("토큰 갱신 실패: {}", e.getMessage());
      throw e;
    }
  }

  @Override
  public void logout(String refreshToken) {
    keycloakService.logout(refreshToken);
  }

  @Override
  public TokenResponseDto exchangeGoogleCode(String code, String codeVerifier, String redirectUri) {
    return keycloakService.exchangePkceCode(code, codeVerifier, redirectUri);
  }

  @Override
  public User getOrCreateProfile(String keycloakId, String email, String nickname) {
    Optional<User> found = userRepository.findByKeycloakId(keycloakId);
    if (found.isEmpty()) {
      if (email == null || email.isBlank()) {
        throw new UserException(UserResponse.USER_NOT_FOUND);
      }
      return createUserFromKeycloak(keycloakId, email, nickname);
    }

    User user = found.get();
    if (email != null && !email.isBlank() && !email.equalsIgnoreCase(user.getEmail())) {
      // 그 email 을 다른 사용자가 쓰면 바꾸지 않음
      if (userRepository.existsByEmail(email)) {
        log.warn("email 을 맞추지 못함 - 다른 사용자가 사용 중: keycloakId={}", keycloakId);
      } else {
        user.changeEmail(email);
        userRepository.save(user);
      }
    }
    return user;
  }

  @Override
  public void deleteAccount(String keycloakId, String password, Instant authTime) {
    User user = userRepository.findByKeycloakIdWithAccounts(keycloakId)
      .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    if (user.getPasswordHash() != null) {
      if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
        log.warn("계정 삭제 실패 - 비밀번호 불일치: keycloakId={}", keycloakId);
        throw new UserException(UserResponse.INVALID_PASSWORD);
      }
    } else if (authTime == null || authTime.isBefore(Instant.now().minus(REAUTH_WINDOW))) {
      log.warn("계정 삭제 실패 - 다시 로그인 필요: keycloakId={}", keycloakId);
      throw new UserException(UserResponse.REAUTH_REQUIRED);
    }

    removeUser(user);

    log.info("계정 삭제 완료: keycloakId={}", keycloakId);
  }

  @Override
  public void changePassword(String keycloakId, String currentPassword, String newPassword) {
    User user = userRepository.findByKeycloakId(keycloakId)
      .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    // 해시가 없는 소셜 사용자도 같은 응답. 401 은 프론트가 세션 만료로 처리하므로 400 을 준다
    if (user.getPasswordHash() == null
      || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      log.warn("비밀번호 변경 실패 - 현재 비밀번호 불일치: keycloakId={}", keycloakId);
      throw new UserException(UserResponse.INVALID_PASSWORD);
    }

    // 로그인이 로컬 해시를 먼저 보므로 Keycloak 과 로컬을 같이 바꾼다
    try {
      keycloakService.resetPassword(user.getKeycloakId(), newPassword);
    } catch (Exception e) {
      log.error("Keycloak 비밀번호 변경 실패: keycloakId={}", keycloakId, e);
      throw new UserException(UserResponse.PASSWORD_CHANGE_FAILED);
    }

    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }


  @Override
  public User updateProfile(String keycloakId, UpdateProfileRequestDto request) {
    User user = userRepository.findByKeycloakId(keycloakId)
      .orElseThrow(() -> {
        log.warn("프로필 수정 실패 - 사용자 없음: keycloakId={}", keycloakId);
        return new UserException(UserResponse.USER_NOT_FOUND);
      });

    if (request.getNickname() != null) {
      if (userRepository.existsByNickname(request.getNickname()) &&
        !request.getNickname().equals(user.getNickname())) {
        log.warn("프로필 수정 실패 - 닉네임 중복: {}", request.getNickname());
        throw new UserException(UserResponse.NICKNAME_ALREADY_EXISTS);
      }
      user.changeNickname(request.getNickname());
    }

    User updatedUser = userRepository.save(user);
    log.debug("프로필 수정 완료: keycloakId={}", keycloakId);

    return updatedUser;
  }


  @Override
  public User verifyEmail(String token) {
    EmailToken emailToken = emailTokenRepository.findByTokenWithUser(token)
      .orElseThrow(() -> {
        log.warn("유효하지 않은 이메일 인증 토큰: {}", token);
        return new UserException(UserResponse.EMAIL_TOKEN_INVALID);
      });

    if (emailToken.isExpired()) {
      log.warn("만료된 이메일 인증 토큰: {}", token);
      throw new UserException(UserResponse.EMAIL_TOKEN_EXPIRED);
    }

    User user = emailToken.getUser();
    user.verifyEmail();

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

    // 가입 여부 · 인증 여부를 응답으로 드러내지 않는다. 보낼 사람에게만 보내고 나머지는 같은 응답으로 끝낸다
    Optional<User> user = userRepository.findByEmail(email);
    if (user.isEmpty() || user.get().isEmailVerified()) {
      log.info("인증 메일 재발송 대상 아님(없는 사용자 또는 인증됨): {}", email);
      return;
    }

    createAndSendVerificationToken(user.get());
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

    // 가입 여부를 응답으로 드러내지 않는다. 인증된 사용자에게만 보내고 나머지는 같은 응답으로 끝낸다
    Optional<User> user = userRepository.findByEmail(email);
    if (user.isEmpty() || !user.get().isEmailVerified()) {
      log.info("비밀번호 재설정 메일 대상 아님(없는 사용자 또는 미인증): {}", email);
      return;
    }

    createAndSendPasswordResetToken(user.get());
    log.info("비밀번호 재설정 이메일 발송 완료: {}", email);
  }

  @Override
  public void resetPassword(String token, String newPassword) {
    PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenWithUser(token)
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
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    // 토큰 사용 처리 및 삭제
    resetToken.markAsUsed();
    passwordResetTokenRepository.save(resetToken);

    log.info("비밀번호 재설정 완료: {}", user.getEmail());
  }

  // ========== 내부 Methods ==========

  // 탈퇴 · 강제 탈퇴 공통. 잔액은 가상 머니라 보지 않는다(계좌 삭제와 같다). 계좌마다 삭제 이벤트를 보내
  // trade-service 가 거래 · 보유를 지우게 하고, 계좌 내역은 DB 의 ON DELETE CASCADE 로 같이 지워진다
  private void removeUser(User user) {
    emailTokenRepository.deleteByUser(user);
    passwordResetTokenRepository.deleteByUser(user);

    List<Account> accounts = accountRepository.findByUserIdWithUser(user.getId());
    accounts.forEach(account -> accountEventProducer.publishAccountDeleted(account, "회원 탈퇴"));
    accountRepository.deleteAll(accounts);
    log.debug("사용자 계좌 삭제: userId={}, 계좌수={}", user.getId(), accounts.size());

    try {
      keycloakService.deleteUser(user.getKeycloakId());
      log.info("Keycloak 사용자 삭제 완료: {}", user.getKeycloakId());
    } catch (Exception e) {
      log.warn("Keycloak 사용자 삭제 실패하지만 로컬 계정 삭제 진행: {}", e.getMessage());
    }

    userRepository.delete(user);
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
  public User updateUserRole(Long userId, UserRole role, String actorKeycloakId) {
    User user = findById(userId);
    if (user.getKeycloakId().equals(actorKeycloakId)) {
      throw new UserException(UserResponse.CANNOT_CHANGE_OWN_ROLE);
    }

    log.info("사용자 역할 변경: userId={}, oldRole={}, newRole={}",
      userId, user.getRole(), role);

    if (role == UserRole.ADMIN) {
      keycloakService.assignRealmRole(user.getKeycloakId(), ADMIN_REALM_ROLE);
    } else {
      keycloakService.removeRealmRole(user.getKeycloakId(), ADMIN_REALM_ROLE);
      keycloakService.logoutUserSessions(user.getKeycloakId());
    }

    user.changeRole(role);
    return userRepository.save(user);
  }

  @Override
  public User updateUserEnabled(Long userId, boolean enabled) {
    User user = findById(userId);

    log.info("사용자 활성화 상태 변경: userId={}, oldEnabled={}, newEnabled={}",
      userId, user.isEnabled(), enabled);

    // Keycloak 도 꺼야 이미 받은 refresh token 으로 새 토큰을 못 받는다
    try {
      keycloakService.setEnabled(user.getKeycloakId(), enabled);
    } catch (Exception e) {
      log.error("Keycloak 활성화 상태 변경 실패: userId={}", userId, e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR);
    }

    user.changeEnabledStatus(enabled);
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

    if (!nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
      throw new UserException(UserResponse.NICKNAME_ALREADY_EXISTS);
    }
    user.changeNickname(nickname);
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
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    log.info("사용자 비밀번호 강제 변경 완료: {}", user.getEmail());
  }

  @Override
  public User adminVerifyEmail(Long userId) {
    User user = findById(userId);

    log.info("Admin이 사용자 이메일 강제 인증: userId={}, email={}",
      userId, user.getEmail());

    user.verifyEmail();

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

    removeUser(user);

    log.info("사용자 강제 탈퇴 완료: {}", user.getEmail());
  }

}
