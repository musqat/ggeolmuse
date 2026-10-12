package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

@DisplayName("시작할 때 관리자 계정 만들기")
class InitialDataLoaderTest {

  private static final String ADMIN_EMAIL = "admin@example.com";

  private final UserRepository userRepository = mock(UserRepository.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final KeycloakService keycloakService = mock(KeycloakService.class);
  private final InitialDataLoader loader =
    new InitialDataLoader(userRepository, accountRepository, passwordEncoder, keycloakService);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(loader, "adminEmail", ADMIN_EMAIL);
    ReflectionTestUtils.setField(loader, "adminPassword", "Adm1n-pass!");
    ReflectionTestUtils.setField(loader, "adminNickname", "Admin");
    given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.empty());
    given(passwordEncoder.encode("Adm1n-pass!")).willReturn("$2a$10$hash");
    given(userRepository.save(any(User.class))).willAnswer(call -> {
      User user = call.getArgument(0);
      user.setId(1L);
      return user;
    });
  }

  private static HttpClientErrorException status(HttpStatus status) {
    return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null);
  }

  private User savedAdmin() {
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    return saved.getValue();
  }

  @Test
  @DisplayName("관리자가 이미 있으면 아무것도 만들지 않는다")
  void adminExists_DoesNothing() {
    given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.of(User.builder().email(ADMIN_EMAIL).build()));

    loader.run(null);

    verifyNoInteractions(keycloakService, accountRepository);
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("ADMIN_PASSWORD 가 없으면 만들지 않고 넘어간다")
  void noPassword_Skips() {
    ReflectionTestUtils.setField(loader, "adminPassword", " ");

    loader.run(null);

    verifyNoInteractions(keycloakService, accountRepository);
  }

  @Test
  @DisplayName("Keycloak 에 관리자를 만들고, 인증된 ADMIN 로컬 계정과 기본 계좌를 만든다")
  void createsAdminWithAccount() {
    given(keycloakService.createAdminUser(ADMIN_EMAIL, "Adm1n-pass!")).willReturn("kc-admin");

    loader.run(null);

    User admin = savedAdmin();
    assertThat(admin.getKeycloakId()).isEqualTo("kc-admin");
    assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(admin.getProvider()).isEqualTo(AuthType.LOCAL);
    assertThat(admin.isEmailVerified()).isTrue();
    assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$hash");
    ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(account.capture());
    assertThat(account.getValue().getAccountName()).isEqualTo("Admin 기본 계좌");
    assertThat(account.getValue().getAccountNumber()).startsWith("ADMIN-1-");
  }

  @Test
  @DisplayName("Keycloak 에 이미 있으면(409) 그 사용자를 찾아 admin 역할을 넣고, 역할 실패는 넘어간다")
  void keycloakConflict_UsesExistingUser() {
    given(keycloakService.createAdminUser(ADMIN_EMAIL, "Adm1n-pass!")).willThrow(status(HttpStatus.CONFLICT));
    given(keycloakService.findUserByEmail(ADMIN_EMAIL)).willReturn("kc-existing");
    willThrow(new IllegalStateException("already assigned")).given(keycloakService).assignRealmRole("kc-existing", "admin");

    loader.run(null);

    assertThat(savedAdmin().getKeycloakId()).isEqualTo("kc-existing");
  }

  @Test
  @DisplayName("409 인데 기존 사용자도 못 찾으면 시작을 실패시키고 로컬 계정을 만들지 않는다")
  void keycloakConflict_NotFound_Fails() {
    given(keycloakService.createAdminUser(ADMIN_EMAIL, "Adm1n-pass!")).willThrow(status(HttpStatus.CONFLICT));
    given(keycloakService.findUserByEmail(ADMIN_EMAIL)).willReturn(null);

    assertThatThrownBy(() -> loader.run(null))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("Admin 사용자 생성 실패");
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Keycloak 권한이 없으면(403) Keycloak 연결 없이 로컬 관리자만 만든다")
  void keycloakForbidden_LocalOnly() {
    given(keycloakService.createAdminUser(anyString(), anyString())).willThrow(status(HttpStatus.FORBIDDEN));

    loader.run(null);

    assertThat(savedAdmin().getKeycloakId()).isNull();
    verify(accountRepository).save(any(Account.class));
  }
}
