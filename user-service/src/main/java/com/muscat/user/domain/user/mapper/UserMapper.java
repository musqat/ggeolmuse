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
  public User createGoogleUser(String email, String googleId, String nickname, String profileImageUrl) {
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
    return UserResponseDto.builder()
        .id(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .role(user.getRole())
        .provider(user.getProvider().toString())
        .profileImageUrl(user.getProfileImageUrl())
        .emailVerified(user.isEmailVerified())
        .socialEmail(user.getSocialEmail())
        .createdAt(user.getCreatedAt())
        .build();
  }

  // Account 엔티티를 AccountSummaryDto로 변환 (목록용)
  public AccountSummaryDto toAccountSummaryDto(Account account) {
    return AccountSummaryDto.builder()
        .accountId(account.getId())
        .accountName(account.getAccountName())
        .accountNumber(account.getAccountNumber())
        .commissionRate(account.getCommissionRate())
        .usdBalance(account.getBalanceUsd())
        .krwBalance(account.getBalanceKrw())
        .createdAt(account.getCreatedAt())
        .build();
  }

  // Account 엔티티를 AccountResponseDto로 변환 (상세용)
  public AccountResponseDto toAccountResponseDto(Account account) {
    return AccountResponseDto.builder()
        .id(account.getId())
        .accountName(account.getAccountName())
        .accountNumber(account.getAccountNumber())
        .balanceKrw(account.getBalanceKrw())
        .balanceUsd(account.getBalanceUsd())
        .avgExchangeRate(account.getAvgExchangeRate())
        .totalExchangedKrw(account.getTotalExchangedKrw())
        .commissionRate(account.getCommissionRate())
        .slippageRate(account.getSlippageRate())
        .createdAt(account.getCreatedAt())
        .build();
  }

}