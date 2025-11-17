package com.muscat.user.domain.user.mapper;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.domain.account.dto.response.AccountResponseDto;
import com.muscat.user.domain.account.dto.response.AccountSummaryDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserMapper {

  // Google 소셜 로그인 사용자 생성
  public User createGoogleUser(String email, String googleId, String nickname,
    String profileImageUrl) {
    return User.builder()
      .email(email)
      .socialEmail(email)
      .socialId(googleId)
      .nickname(nickname)
      .profileImageUrl(profileImageUrl)
      .provider(AuthType.GOOGLE)
      .emailVerified(true)
      .build();
  }

  // 로컬 회원가입 사용자 생성
  public User createLocalUser(String email, String nickname, String keycloakId) {
    return User.builder()
      .email(email)
      .nickname(nickname)
      .keycloakId(keycloakId)
      .provider(AuthType.LOCAL)
      .emailVerified(false)
      .build();
  }

  // User 엔티티를 UserResponseDto로 변환
  public UserResponseDto toResponseDto(User user) {
    return new UserResponseDto(
      user.getId(),
      user.getEmail(),
      user.getNickname(),
      user.getRole(),
      user.getCreatedAt(),
      user.getProvider().toString(),
      user.isEmailVerified(),
      user.getProfileImageUrl(),
      user.getSocialEmail()
    );
  }

  // Account 엔티티를 AccountSummaryDto로 변환 (목록용)
  public AccountSummaryDto toAccountSummaryDto(Account account) {
    return new AccountSummaryDto(
      account.getId(),
      account.getAccountName(),
      account.getAccountNumber(),
      account.getCommissionRate(),
      account.getBalanceUsd(),
      account.getBalanceKrw(),
      account.getCreatedAt()
    );
  }

  // Account 엔티티를 AccountResponseDto로 변환 (상세용)
  public AccountResponseDto toAccountResponseDto(Account account) {
    return new AccountResponseDto(
      account.getId(),
      account.getAccountName(),
      account.getAccountNumber(),
      account.getCreatedAt(),
      account.getBalanceKrw(),
      account.getBalanceUsd(),
      account.getAvgExchangeRate(),
      account.getTotalExchangedKrw(),
      account.getCommissionRate(),
      account.getSlippageRate()
    );
  }

}
