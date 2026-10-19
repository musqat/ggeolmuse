package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.user.common.enums.responses.KeycloakResponse;
import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exceptions.KeycloakException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
  @DisplayName("Keycloak 에도 없으면 관리자를 만들고, 인증된 ADMIN 로컬 계정과 기본 계좌를 만든다")
  void createsAdminWithAccount() {
    given(keycloakService.findUserIdByUsername(ADMIN_EMAIL)).willReturn(null);
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
  @DisplayName("Keycloak 에서 email 을 바꾼 관리자는 사용자 이름으로 찾은 로컬 계정이 있으면 아무것도 만들지 않는다")
  void adminEmailChanged_FoundByUsername_DoesNothing() {
    given(keycloakService.findUserIdByUsername(ADMIN_EMAIL)).willReturn("kc-admin");
    given(userRepository.findByKeycloakId("kc-admin")).willReturn(Optional.of(User.builder().email("new@example.com").build()));

    loader.run(null);

    verify(keycloakService, never()).createAdminUser(anyString(), anyString());
    verify(keycloakService, never()).assignRealmRole(anyString(), anyString());
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Keycloak 에만 있는 관리자 계정은 admin 역할을 부여하고 로컬 계정을 만든다")
  void keycloakOnlyAdmin_AssignsRoleAndSaves() {
    given(keycloakService.findUserIdByUsername(ADMIN_EMAIL)).willReturn("kc-admin");
    given(userRepository.findByKeycloakId("kc-admin")).willReturn(Optional.empty());

    loader.run(null);

    verify(keycloakService).assignRealmRole("kc-admin", "admin");
    verify(keycloakService, never()).createAdminUser(anyString(), anyString());
    assertThat(savedAdmin().getKeycloakId()).isEqualTo("kc-admin");
  }

  @Test
  @DisplayName("Keycloak 호출이 실패하면 시작을 실패시키고 로컬 계정을 만들지 않는다")
  void keycloakFailure_Fails() {
    given(keycloakService.createAdminUser(ADMIN_EMAIL, "Adm1n-pass!"))
      .willThrow(new KeycloakException(KeycloakResponse.USER_CREATE_FAILED));

    assertThatThrownBy(() -> loader.run(null))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("Admin 사용자 생성 실패");
    verify(userRepository, never()).save(any());
  }
}
