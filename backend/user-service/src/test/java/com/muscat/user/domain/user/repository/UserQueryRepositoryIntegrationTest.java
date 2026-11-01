package com.muscat.user.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

// 트랜잭션 없이 호출(fetch join 이 빠지면 지연 로딩한 연관을 읽을 때 오류)
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("사용자 · 토큰 QueryDSL 조회 (fetch join)")
class UserQueryRepositoryIntegrationTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private EmailTokenRepository emailTokenRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  private User user;

  @BeforeEach
  void setUp() {
    user = userRepository.save(User.builder().email("query@example.com").nickname("조회").keycloakId("kc-query").build());
  }

  @AfterEach
  void tearDown() {
    emailTokenRepository.deleteAll();
    passwordResetTokenRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("이메일 인증 토큰은 사용자와 함께 찾고, 없는 토큰은 비어 있다")
  void emailToken_WithUser() {
    EmailToken token = new EmailToken();
    token.setToken("email-token-1");
    token.setUser(user);
    token.setExpiryDate(LocalDateTime.now().plusHours(24));
    emailTokenRepository.save(token);

    assertThat(emailTokenRepository.findByTokenWithUser("email-token-1"))
      .hasValueSatisfying(found -> assertThat(found.getUser().getEmail()).isEqualTo("query@example.com"));
    assertThat(emailTokenRepository.findByTokenWithUser("unknown")).isEmpty();
  }

  @Test
  @DisplayName("비밀번호 재설정 토큰은 사용자와 함께 찾는다")
  void passwordResetToken_WithUser() {
    PasswordResetToken token = new PasswordResetToken();
    token.setToken("reset-token-1");
    token.setUser(user);
    token.setExpiryDate(LocalDateTime.now().plusMinutes(30));
    passwordResetTokenRepository.save(token);

    assertThat(passwordResetTokenRepository.findByTokenWithUser("reset-token-1"))
      .hasValueSatisfying(found -> assertThat(found.getUser().getKeycloakId()).isEqualTo("kc-query"));
    assertThat(passwordResetTokenRepository.findByTokenWithUser("unknown")).isEmpty();
  }

  @Test
  @DisplayName("Keycloak ID 로 사용자를 계좌와 함께 찾는다. 계좌가 없어도 사용자는 나온다")
  void userWithAccounts() {
    assertThat(userRepository.findByKeycloakIdWithAccounts("kc-query"))
      .hasValueSatisfying(found -> assertThat(found.getAccounts()).isEmpty());

    accountRepository.save(Account.builder().user(user).accountNumber("ACC0000000000101").accountName("하나").build());
    accountRepository.save(Account.builder().user(user).accountNumber("ACC0000000000102").accountName("둘").build());

    assertThat(userRepository.findByKeycloakIdWithAccounts("kc-query"))
      .hasValueSatisfying(found -> assertThat(found.getAccounts()).extracting(Account::getAccountName)
        .containsExactlyInAnyOrder("하나", "둘"));
    assertThat(userRepository.findByKeycloakIdWithAccounts("kc-none")).isEmpty();
  }

  @Test
  @DisplayName("같은 Keycloak ID 로 다른 사용자를 저장하면 막는다")
  void save_DuplicateKeycloakId_Rejected() {
    assertThatThrownBy(() -> userRepository.saveAndFlush(
      User.builder().email("other@example.com").nickname("다른").keycloakId("kc-query").build()))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("같은 닉네임으로 다른 사용자를 저장하면 막고, 대소문자가 다르거나 닉네임이 비면 저장한다")
  void save_DuplicateNickname_Rejected() {
    assertThatThrownBy(() -> userRepository.saveAndFlush(
      User.builder().email("same@example.com").nickname("조회").keycloakId("kc-same").build()))
      .isInstanceOf(DataIntegrityViolationException.class);

    userRepository.saveAndFlush(User.builder().email("upper@example.com").nickname("Query").keycloakId("kc-upper").build());
    userRepository.saveAndFlush(User.builder().email("lower@example.com").nickname("query").keycloakId("kc-lower").build());
    userRepository.saveAndFlush(User.builder().email("none1@example.com").keycloakId("kc-none1").build());
    userRepository.saveAndFlush(User.builder().email("none2@example.com").keycloakId("kc-none2").build());

    assertThat(userRepository.count()).isEqualTo(5);
  }

  @Test
  @DisplayName("Keycloak ID 없는 사용자는 저장하지 않는다")
  void save_NullKeycloakId_Rejected() {
    assertThatThrownBy(() -> userRepository.saveAndFlush(
      User.builder().email("nokc@example.com").nickname("없음").build()))
      .isInstanceOf(DataIntegrityViolationException.class);
  }
}
