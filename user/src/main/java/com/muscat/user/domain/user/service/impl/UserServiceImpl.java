package com.muscat.user.domain.user.service.impl;

import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.repository.EmailTokenRepository;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
import com.muscat.user.mail.MailService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
  private final AccountRepository accountRepository;
  private final MailService mailService;
  private final KeycloakService keycloakService;
  private final PasswordEncoder passwordEncoder;
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
      // 1. Keycloak 사용자 생성 먼저 확인 (중복 체크)
      String keycloakId = keycloakService.createUser(email, password);
      log.info("Keycloak 사용자 생성 성공: {}", email);
      
      // 2. 로컬 사용자 생성 (Keycloak 성공 후)
      User user = userMapper.createLocalUser(email, nickname, keycloakId);
      user.setPasswordHash(passwordEncoder.encode(password));
      
      User savedUser = userRepository.save(user);

      createAndSendVerificationToken(savedUser);

      log.info("회원가입 성공: {}", email);
      return savedUser;

    } catch (UserException e) {
      throw e;
    } catch (KeycloakException e) {
      // Keycloak 예외는 그대로 전파 (SYSTEM 타입 유지)
      log.error("Keycloak 연동 실패: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("회원가입 처리 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR, "회원가입 처리 중 오류가 발생했습니다.");
    }
  }


  @Override
  public String login(String email, String password) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> {
          log.warn("로그인 실패 - 사용자 없음: {}", email);
          return new AuthenticationException(UserResponse.INVALID_CREDENTIALS);
        });

    if (!user.isEmailVerified()) {
      log.warn("로그인 실패 - 이메일 미인증: {}", email);
      throw new UserException(UserResponse.EMAIL_NOT_VERIFIED);
    }

    try {
      // 1. 로컬 비밀번호 검증 (Primary)
      if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
        log.warn("로그인 실패 - 비밀번호 불일치: {}", email);
        throw new UserException(UserResponse.INVALID_CREDENTIALS);
      }
      
      // 2. Keycloak 토큰 발급 시도 (Secondary)  
      String token = keycloakService.login(email, password);
      log.info("로그인 성공: {}", email);
      return token;
      
    } catch (UserException e) {
      throw e;
    } catch (Exception e) {
      log.error("로그인 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
      throw new UserException(UserResponse.AUTHENTICATION_FAILED);
    }
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

    try {
      // 로컬 비밀번호 검증
      if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
        log.warn("계정 삭제 실패 - 비밀번호 불일치: {}", email);
        throw new UserException(UserResponse.INVALID_PASSWORD);
      }

      user.validateForDeletion();

      deleteUserRelatedData(user);

      // Keycloak 사용자 삭제 시도 (실패해도 로컬 삭제 진행)
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

    } catch (AuthenticationException e) {
      throw new UserException(UserResponse.INVALID_PASSWORD);
    } catch (UserException e) {
      throw e;
    } catch (Exception e) {
      log.error("계정 삭제 중 오류 발생: {}", e.getMessage(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR, "계정 삭제 중 오류가 발생했습니다.");
    }
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

  // ========== 내부 메서드들 ==========

  // 사용자 관련 데이터 정리
  private void deleteUserRelatedData(User user) {
    try {
      emailTokenRepository.deleteByUser(user);

      List<Account> accounts = accountRepository.findByUserIdWithUser(user.getId());
      validateAccountsForDeletion(accounts);

      log.debug("사용자 관련 데이터 정리 완료: userId={}, 계좌수={}", user.getId(), accounts.size());

    } catch (Exception e) {
      log.error("사용자 관련 데이터 정리 실패: userId={}", user.getId(), e);
      throw new UserException(UserResponse.INTERNAL_SERVER_ERROR,
          "관련 데이터 정리 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  // 계좌들 삭제 가능 여부 일괄 검증
  private void validateAccountsForDeletion(List<Account> accounts) {
    for (Account account : accounts) {
      if (account.getBalanceKrw().compareTo(BigDecimal.ZERO) > 0 ||
          account.getBalanceUsd().compareTo(BigDecimal.ZERO) > 0) {
        throw new UserException(UserResponse.ACCOUNT_DELETION_BLOCKED,
            String.format("계좌 '%s'에 잔액이 있어 삭제할 수 없습니다.", account.getAccountName()));
      }
    }
  }

  // 이메일 인증 토큰 생성 및 발송
  private void createAndSendVerificationToken(User user) {
    try {
      emailTokenRepository.deleteByUser(user);

      EmailToken token = new EmailToken();
      token.setToken(UUID.randomUUID().toString());
      token.setUser(user);
      token.setExpiryDate(LocalDateTime.now().plusHours(expiryHours));

      emailTokenRepository.save(token);

      mailService.sendVerificationEmail(user.getEmail(), token.getToken());

      log.debug("이메일 인증 토큰 생성 및 발송 완료: {}", user.getEmail());
    } catch (Exception e) {
      log.error("이메일 발송 실패: {}", e.getMessage(), e);
      throw new UserException(UserResponse.EMAIL_SEND_FAILED);
    }
  }

}
