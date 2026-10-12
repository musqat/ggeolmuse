package com.muscat.user.domain.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.account.dto.response.AccountResponseDto;
import com.muscat.user.domain.account.dto.response.AccountSummaryDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사용자 · 계좌 응답 변환")
class UserMapperTest {

  private final UserMapper mapper = new UserMapper();

  @Test
  @DisplayName("로컬 가입 사용자는 LOCAL · 이메일 미인증으로 만든다")
  void createLocalUser() {
    User user = mapper.createLocalUser("user@example.com", "투자자", "kc-1");

    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getKeycloakId()).isEqualTo("kc-1");
    assertThat(user.getProvider()).isEqualTo(AuthType.LOCAL);
    assertThat(user.isEmailVerified()).isFalse();
  }

  @Test
  @DisplayName("비밀번호가 있는 사용자만 hasPassword 가 참이다(탈퇴 · 비밀번호 변경 화면이 이 값으로 갈린다)")
  void toResponseDto_HasPassword() {
    User local = User.builder().id(1L).email("user@example.com").nickname("투자자").role(UserRole.USER)
      .provider(AuthType.LOCAL).passwordHash("$2a$10$hash").emailVerified(true).build();
    User google = User.builder().id(2L).email("g@example.com").nickname("구글").role(UserRole.USER)
      .provider(AuthType.GOOGLE).socialEmail("g@gmail.com").profileImageUrl("https://example.com/p.png").build();

    UserResponseDto localDto = mapper.toResponseDto(local);
    UserResponseDto googleDto = mapper.toResponseDto(google);

    assertThat(localDto.hasPassword()).isTrue();
    assertThat(localDto.provider()).isEqualTo("LOCAL");
    assertThat(localDto.emailVerified()).isTrue();
    assertThat(googleDto.hasPassword()).isFalse();
    assertThat(googleDto.provider()).isEqualTo("GOOGLE");
    assertThat(googleDto.socialEmail()).isEqualTo("g@gmail.com");
    assertThat(googleDto.profileImageUrl()).isEqualTo("https://example.com/p.png");
  }

  @Test
  @DisplayName("계좌 요약 · 상세는 잔액과 수수료율을 그대로 옮긴다")
  void accountDtos() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 9, 0);
    Account account = Account.builder().id(3L).accountName("연습").accountNumber("ACC0000000000001")
      .balanceKrw(new BigDecimal("150000")).balanceUsd(new BigDecimal("12.50"))
      .avgExchangeRate(new BigDecimal("1300")).totalExchangedKrw(new BigDecimal("16250"))
      .commissionRate(new BigDecimal("0.001")).slippageRate(new BigDecimal("0.002")).build();
    account.setCreatedAt(createdAt);

    AccountSummaryDto summary = mapper.toAccountSummaryDto(account);
    AccountResponseDto detail = mapper.toAccountResponseDto(account);

    assertThat(summary).isEqualTo(new AccountSummaryDto(3L, "연습", "ACC0000000000001", new BigDecimal("0.001"),
      new BigDecimal("12.50"), new BigDecimal("150000"), createdAt));
    assertThat(detail).isEqualTo(new AccountResponseDto(3L, "연습", "ACC0000000000001", createdAt,
      new BigDecimal("150000"), new BigDecimal("12.50"), new BigDecimal("1300"), new BigDecimal("16250"),
      new BigDecimal("0.001"), new BigDecimal("0.002")));
  }
}
