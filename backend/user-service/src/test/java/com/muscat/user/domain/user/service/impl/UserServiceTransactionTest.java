package com.muscat.user.domain.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

import com.muscat.user.config.mail.MailService;
import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.EmailTokenRepository;
import com.muscat.user.domain.user.repository.PasswordResetTokenRepository;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
import com.muscat.user.infra.kafka.AccountEventProducer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserService 의 Keycloak 호출은 DB 트랜잭션 밖")
class UserServiceTransactionTest {

  private static final String EMAIL = "tx@example.com";
  private static final String PASSWORD = "password123";
  private static final String NEW_PASSWORD = "newPassword123";
  private static final String KEYCLOAK_ID = "kc-tx";

  @Autowired
  private UserService userService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EmailTokenRepository emailTokenRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private KeycloakService keycloakService;

  @MockitoBean
  private AccountEventProducer accountEventProducer;

  @MockitoBean
  private MailService mailService;

  // Keycloak 호출마다 그때 DB 트랜잭션이 열려 있었는지
  private final List<Boolean> inTransaction = new ArrayList<>();

  private Answer<Object> recording(Object result) {
    return invocation -> {
      inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
      return result;
    };
  }

  private User savedUser() {
    return userRepository.save(User.builder()
      .email(EMAIL)
      .passwordHash(passwordEncoder.encode(PASSWORD))
      .nickname("트랜잭션")
      .keycloakId(KEYCLOAK_ID)
      .emailVerified(true)
      .build());
  }

  @AfterEach
  void tearDown() {
    passwordResetTokenRepository.deleteAll();
    emailTokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("로그인은 트랜잭션 없이 Keycloak 토큰을 받는다")
  void login_CallsKeycloakOutsideTransaction() {
    savedUser();
    willAnswer(recording(null)).given(keycloakService).login(EMAIL, PASSWORD);

    userService.login(EMAIL, PASSWORD);

    assertThat(inTransaction).containsExactly(false);
  }

  @Test
  @DisplayName("가입은 트랜잭션 밖에서 Keycloak 계정을 만들고 사용자를 저장한다")
  void register_CreatesKeycloakUserOutsideTransaction() {
    willAnswer(recording(KEYCLOAK_ID)).given(keycloakService).createUser(EMAIL, PASSWORD);

    userService.registerUser(EMAIL, PASSWORD, "가입자");

    assertThat(inTransaction).containsExactly(false);
    assertThat(userRepository.findByKeycloakId(KEYCLOAK_ID)).isPresent();
  }

  @Test
  @DisplayName("비밀번호 변경은 트랜잭션 밖에서 Keycloak 비밀번호를 바꾸고 세션을 끝낸다")
  void changePassword_CallsKeycloakOutsideTransaction() {
    savedUser();
    willAnswer(recording(null)).given(keycloakService).resetPassword(KEYCLOAK_ID, NEW_PASSWORD);
    willAnswer(recording(null)).given(keycloakService).logoutUserSessions(KEYCLOAK_ID);

    userService.changePassword(KEYCLOAK_ID, PASSWORD, NEW_PASSWORD);

    assertThat(inTransaction).containsExactly(false, false);
    assertThat(passwordEncoder.matches(NEW_PASSWORD,
      userRepository.findByKeycloakId(KEYCLOAK_ID).orElseThrow().getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("비밀번호 재설정은 트랜잭션 밖에서 Keycloak 비밀번호를 바꾸고 토큰을 쓴 것으로 둔다")
  void resetPassword_CallsKeycloakOutsideTransaction() {
    User user = savedUser();
    PasswordResetToken token = new PasswordResetToken();
    token.setToken("reset-tx");
    token.setUser(user);
    token.setExpiryDate(LocalDateTime.now().plusMinutes(30));
    passwordResetTokenRepository.save(token);
    willAnswer(recording(null)).given(keycloakService).resetPassword(KEYCLOAK_ID, NEW_PASSWORD);
    willAnswer(recording(null)).given(keycloakService).logoutUserSessions(KEYCLOAK_ID);

    userService.resetPassword("reset-tx", NEW_PASSWORD);

    assertThat(inTransaction).containsExactly(false, false);
    assertThat(passwordResetTokenRepository.findAll()).singleElement()
      .extracting(PasswordResetToken::isUsed).isEqualTo(true);
  }

  @Test
  @DisplayName("관리자 비활성 전환은 트랜잭션 밖에서 Keycloak 상태를 바꾼다")
  void updateUserEnabled_CallsKeycloakOutsideTransaction() {
    User user = savedUser();
    willAnswer(recording(null)).given(keycloakService).setEnabled(anyString(), anyBoolean());

    userService.updateUserEnabled(user.getId(), false);

    assertThat(inTransaction).containsExactly(false);
    assertThat(userRepository.findById(user.getId()).orElseThrow().isEnabled()).isFalse();
  }

  @Test
  @DisplayName("탈퇴는 DB 를 지운 뒤 트랜잭션 밖에서 Keycloak 계정을 지운다")
  void deleteAccount_DeletesKeycloakUserOutsideTransaction() {
    savedUser();
    willAnswer(recording(null)).given(keycloakService).deleteUser(KEYCLOAK_ID);

    userService.deleteAccount(KEYCLOAK_ID, PASSWORD, null);

    assertThat(inTransaction).containsExactly(false);
    assertThat(userRepository.findByKeycloakId(KEYCLOAK_ID)).isEmpty();
  }

  @Test
  @DisplayName("구글 코드 교환 · 토큰 갱신 · 로그아웃은 트랜잭션 없이 Keycloak 을 부른다")
  void tokenCalls_OutsideTransaction() {
    willAnswer(recording(null)).given(keycloakService).exchangePkceCode(anyString(), anyString(), anyString());
    willAnswer(recording(null)).given(keycloakService).refreshToken(anyString());
    willAnswer(recording(null)).given(keycloakService).logout(any());

    userService.exchangeGoogleCode("code", "verifier", "http://localhost/cb");
    userService.refreshToken("refresh");
    userService.logout("refresh");

    assertThat(inTransaction).containsExactly(false, false, false);
  }
}
