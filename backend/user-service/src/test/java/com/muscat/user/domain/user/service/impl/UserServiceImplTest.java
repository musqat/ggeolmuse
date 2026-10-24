package com.muscat.user.domain.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.user.common.enums.response.KeycloakResponse;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exception.AuthenticationException;
import com.muscat.user.common.exception.KeycloakException;
import com.muscat.user.common.exception.UserException;
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
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private EmailTokenRepository emailTokenRepository;

  @Mock
  private com.muscat.user.domain.user.repository.PasswordResetTokenRepository passwordResetTokenRepository;

  @Mock
  private AccountRepository accountRepository;

  @Mock
  private MailService mailService;

  @Mock
  private KeycloakService keycloakService;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private UserMapper userMapper;

  @Mock
  private com.muscat.user.common.util.RateLimitService rateLimitService;

  @Mock
  private com.muscat.user.infra.kafka.LoginEventProducer loginEventProducer;

  @Mock
  private com.muscat.user.infra.kafka.AccountEventProducer accountEventProducer;

  @InjectMocks
  private UserServiceImpl userService;

  private String testEmail;
  private String testPassword;
  private String testNickname;
  private String keycloakId;
  private User testUser;

  @BeforeEach
  void setUp() {
    testEmail = "test@example.com";
    testPassword = "password123";
    testNickname = "TestUser";
    keycloakId = "keycloak-123";

    testUser = User.builder()
      .id(1L)
      .email(testEmail)
      .nickname(testNickname)
      .keycloakId(keycloakId)
      .passwordHash("$2a$10$encodedPassword")
      .provider(AuthType.LOCAL)
      .role(UserRole.USER)
      .emailVerified(true)
      .enabled(true)
      .build();

    ReflectionTestUtils.setField(userService, "expiryHours", 24);
  }

  @Nested
  @DisplayName("사용자 등록 테스트")
  class RegisterUserTests {

    @Test
    @DisplayName("정상적으로 사용자가 등록된다")
    void registerUser_Success() {
      // given
      String rawPassword = "password123";
      String encodedPassword = "$2a$10$encodedPassword";

      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, rawPassword)).willReturn(keycloakId);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(testUser);
      given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
      given(userRepository.save(any(User.class))).willReturn(testUser);
      given(emailTokenRepository.save(any(EmailToken.class))).willReturn(new EmailToken());

      // when
      User result = userService.registerUser(testEmail, rawPassword, testNickname);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getEmail()).isEqualTo(testEmail);
      assertThat(result.getNickname()).isEqualTo(testNickname);

      verify(userRepository).existsByEmail(testEmail);
      verify(keycloakService).createUser(testEmail, rawPassword);
      verify(userRepository).save(any(User.class));
      verify(emailTokenRepository).deleteByUser(testUser);
      verify(emailTokenRepository).save(any(EmailToken.class));
      verify(mailService).sendVerificationEmail(eq(testEmail), anyString());
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 예외가 발생한다")
    void registerUser_EmailAlreadyExists_ThrowsException() {
      // given
      given(userRepository.existsByEmail(testEmail)).willReturn(true);

      // when & then
      assertThatThrownBy(() ->
        userService.registerUser(testEmail, testPassword, testNickname)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_ALREADY_EXISTS.getMessage());

      verify(keycloakService, never()).createUser(anyString(), anyString());
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Keycloak 사용자 생성 실패 시 예외가 발생한다")
    void registerUser_KeycloakFailure_ThrowsException() {
      // given
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, testPassword))
        .willThrow(new KeycloakException(KeycloakResponse.USER_CREATE_FAILED));

      // when & then
      assertThatThrownBy(() ->
        userService.registerUser(testEmail, testPassword, testNickname)
      ).isInstanceOf(KeycloakException.class);

      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Keycloak 에 이미 있는 이메일(409)은 연결하지 않고 이미 가입된 이메일로 막는다")
    void registerUser_KeycloakConflict_EmailAlreadyExists() {
      // given
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, testPassword)).willThrow(
        org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.CONFLICT,
          "Conflict", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

      // when & then
      assertThatThrownBy(() -> userService.registerUser(testEmail, testPassword, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_ALREADY_EXISTS.getMessage());
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("DB 저장이 실패하면 먼저 만든 Keycloak 계정을 지운다")
    void registerUser_LocalSaveFailure_DeletesKeycloakUser() {
      // given
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, testPassword)).willReturn(keycloakId);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(testUser);
      given(passwordEncoder.encode(testPassword)).willReturn("$2a$10$encodedPassword");
      given(userRepository.save(any(User.class)))
        .willThrow(new org.springframework.dao.DataAccessResourceFailureException("DB 연결 실패"));

      // when & then
      assertThatThrownBy(() -> userService.registerUser(testEmail, testPassword, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
      verify(keycloakService).deleteUser(keycloakId);
    }

    @Test
    @DisplayName("Keycloak 계정 지우기까지 실패해도 처음 오류로 응답한다")
    void registerUser_LocalSaveAndCleanupFailure_KeepsOriginalError() {
      // given
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, testPassword)).willReturn(keycloakId);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(testUser);
      given(passwordEncoder.encode(testPassword)).willReturn("$2a$10$encodedPassword");
      given(userRepository.save(any(User.class)))
        .willThrow(new org.springframework.dao.DataAccessResourceFailureException("DB 연결 실패"));
      willThrow(new KeycloakException(KeycloakResponse.USER_DELETE_FAILED))
        .given(keycloakService).deleteUser(keycloakId);

      // when & then
      assertThatThrownBy(() -> userService.registerUser(testEmail, testPassword, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
    }

    @Test
    @DisplayName("이메일 발송 실패 시에도 사용자 등록은 성공한다")
    void registerUser_EmailSendFailure_StillSucceeds() {
      // given
      String rawPassword = "password123";
      String encodedPassword = "$2a$10$encodedPassword";

      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, rawPassword)).willReturn(keycloakId);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(testUser);
      given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
      given(userRepository.save(any(User.class))).willReturn(testUser);
      given(emailTokenRepository.save(any(EmailToken.class))).willReturn(new EmailToken());
      doThrow(new RuntimeException("SMTP connection failed"))
        .when(mailService).sendVerificationEmail(anyString(), anyString());

      // when
      User result = userService.registerUser(testEmail, rawPassword, testNickname);

      // then
      assertThat(result).isNotNull();
      verify(userRepository).save(any(User.class));
      verify(mailService).sendVerificationEmail(eq(testEmail), anyString());
    }
  }

  @Nested
  @DisplayName("로그인 테스트")
  class LoginTests {

    @Test
    @DisplayName("정상적으로 로그인이 성공하고 access · refresh token 을 그대로 돌려준다")
    void login_Success() {
      // given
      TokenResponseDto jwtToken = new TokenResponseDto("jwt.token.here", "refresh.token.here", 3600, 7200);
      String rawPassword = "password123";

      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setEmailVerified(true);

      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(rawPassword, testUser.getPasswordHash())).willReturn(true);
      given(keycloakService.login(testEmail, rawPassword)).willReturn(jwtToken);

      // when
      TokenResponseDto result = userService.login(testEmail, rawPassword);

      // then
      assertThat(result).isEqualTo(jwtToken);
      verify(userRepository).findByEmail(testEmail);
      verify(passwordEncoder).matches(rawPassword, testUser.getPasswordHash());
      verify(keycloakService).login(testEmail, rawPassword);
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 예외가 발생한다")
    void login_UserNotFound_ThrowsException() {
      // given
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
        userService.login(testEmail, testPassword)
      ).isInstanceOf(AuthenticationException.class)
        .hasMessage(UserResponse.INVALID_CREDENTIALS.getMessage());

      verify(keycloakService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호가 맞아도 비활성 계정이면 로그인할 수 없다")
    void login_Disabled_Rejected() {
      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setEmailVerified(true);
      testUser.changeEnabledStatus(false);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);

      assertThatThrownBy(() -> userService.login(testEmail, testPassword))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.ACCOUNT_DISABLED.getMessage());

      verify(keycloakService, never()).login(anyString(), anyString());
      verify(loginEventProducer).publishLoginFailed(eq(testEmail), eq("ACCOUNT_DISABLED"), anyString(),
        eq("PASSWORD"), any(), any());
    }

    @Test
    @DisplayName("비밀번호가 맞아도 이메일 미인증이면 로그인할 수 없다")
    void login_EmailNotVerified_ThrowsException() {
      // given: 미인증 사용자 + 올바른 비밀번호
      // 비밀번호 검증이 인증 체크보다 먼저 돈다. 통과한 사람에게만 미인증 안내를 준다.
      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setEmailVerified(false);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);

      // when & then
      assertThatThrownBy(() ->
        userService.login(testEmail, testPassword)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_NOT_VERIFIED.getMessage());

      verify(keycloakService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("미인증 사용자라도 비밀번호가 틀리면 인증 실패로 응답한다(가입 여부 노출 차단)")
    void login_EmailNotVerified_WrongPassword_HidesExistence() {
      // given: 미인증 사용자 + 틀린 비밀번호
      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setEmailVerified(false);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash())).willReturn(false);

      // when & then: 없는 계정과 같은 INVALID_CREDENTIALS 로 응답해 열거를 막는다
      assertThatThrownBy(() ->
        userService.login(testEmail, "wrongPassword")
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_CREDENTIALS.getMessage());

      verify(keycloakService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 예외가 발생한다")
    void login_InvalidPassword_ThrowsException() {
      // given
      String wrongPassword = "wrongPassword";
      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setEmailVerified(true);

      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).willReturn(false);

      // when & then
      assertThatThrownBy(() ->
        userService.login(testEmail, wrongPassword)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_CREDENTIALS.getMessage());

      verify(keycloakService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호가 null이면 예외가 발생한다")
    void login_NullPassword_ThrowsException() {
      // given
      testUser.setPasswordHash(null);
      testUser.setEmailVerified(true);

      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

      // when & then
      assertThatThrownBy(() ->
        userService.login(testEmail, testPassword)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_CREDENTIALS.getMessage());
    }
  }

  @Nested
  @DisplayName("토큰 갱신 · 로그아웃 테스트")
  class TokenSessionTests {

    @Test
    @DisplayName("갱신은 Keycloak 이 준 두 토큰을 그대로 돌려준다")
    void refreshToken_ReturnsKeycloakTokens() {
      TokenResponseDto tokens = new TokenResponseDto("new-access", "new-refresh", 3600, 7200);
      given(keycloakService.refreshToken("old-refresh")).willReturn(tokens);

      assertThat(userService.refreshToken("old-refresh")).isEqualTo(tokens);
    }

    @Test
    @DisplayName("갱신이 실패하면 예외를 그대로 올린다")
    void refreshToken_Failure_Rethrows() {
      given(keycloakService.refreshToken("expired"))
        .willThrow(new KeycloakException(KeycloakResponse.TOKEN_REFRESH_FAILED));

      assertThatThrownBy(() -> userService.refreshToken("expired"))
        .isInstanceOf(KeycloakException.class);
    }

    @Test
    @DisplayName("로그아웃은 Keycloak 세션 종료로 넘긴다")
    void logout_DelegatesToKeycloak() {
      userService.logout("refresh-1");

      verify(keycloakService).logout("refresh-1");
    }

    @Test
    @DisplayName("구글 인가 코드 교환은 Keycloak PKCE 교환으로 넘긴다")
    void exchangeGoogleCode_DelegatesToKeycloak() {
      TokenResponseDto tokens = new TokenResponseDto("a", "r", 3600, 7200);
      given(keycloakService.exchangePkceCode("c", "v", "https://app.example/oauth/callback")).willReturn(tokens);

      assertThat(userService.exchangeGoogleCode("c", "v", "https://app.example/oauth/callback")).isEqualTo(tokens);
    }
  }

  @Nested
  @DisplayName("내 프로필 찾기 · 만들기")
  class GetOrCreateProfileTests {

    private static final String NEW_EMAIL = "new@example.com";

    @Test
    @DisplayName("Keycloak ID 로 찾은 사용자를 돌려주고, email 이 같으면 저장하지 않는다")
    void getOrCreateProfile_Existing_ReturnsUser() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));

      User result = userService.getOrCreateProfile(keycloakId, testEmail, testNickname);

      assertThat(result).isSameAs(testUser);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Keycloak ID 로 없으면 토큰 정보로 만든다")
    void getOrCreateProfile_Missing_CreatesFromToken() {
      User created = User.builder().id(2L).keycloakId(keycloakId).email(testEmail).nickname(testNickname).build();
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.empty());
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(created);
      given(userRepository.save(created)).willReturn(created);

      User result = userService.getOrCreateProfile(keycloakId, testEmail, testNickname);

      assertThat(result.getKeycloakId()).isEqualTo(keycloakId);
      assertThat(result.getEmail()).isEqualTo(testEmail);
      assertThat(result.getNickname()).isEqualTo(testNickname);
      verify(userRepository).save(created);
    }

    @Test
    @DisplayName("Keycloak 에서 email 이 바뀌었으면 새 행을 만들지 않고 DB email 을 토큰 값으로 바꾼다")
    void getOrCreateProfile_EmailChanged_UpdatesEmail() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(userRepository.existsByEmail(NEW_EMAIL)).willReturn(false);
      given(userRepository.save(testUser)).willReturn(testUser);

      User result = userService.getOrCreateProfile(keycloakId, NEW_EMAIL, testNickname);

      assertThat(result.getEmail()).isEqualTo(NEW_EMAIL);
      verify(userRepository).save(testUser);
      verify(userMapper, never()).createLocalUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("토큰 email 을 다른 사용자가 쓰고 있으면 바꾸지 않고 그대로 돌려준다")
    void getOrCreateProfile_EmailTakenByOther_KeepsEmail() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(userRepository.existsByEmail(NEW_EMAIL)).willReturn(true);

      User result = userService.getOrCreateProfile(keycloakId, NEW_EMAIL, testNickname);

      assertThat(result.getEmail()).isEqualTo(testEmail);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("대소문자만 다르면 같은 email 로 보고 바꾸지 않는다")
    void getOrCreateProfile_CaseOnlyDifference_KeepsEmail() {
      testUser.changeEmail("Test@Example.com");
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));

      User result = userService.getOrCreateProfile(keycloakId, "test@example.com", testNickname);

      assertThat(result.getEmail()).isEqualTo("Test@Example.com");
      verify(userRepository, never()).existsByEmail(anyString());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("토큰에 email 이 없으면 찾은 사용자를 그대로 돌려준다")
    void getOrCreateProfile_NoEmailClaim_ReturnsExisting() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));

      User result = userService.getOrCreateProfile(keycloakId, null, null);

      assertThat(result).isSameAs(testUser);
      verify(userRepository, never()).existsByEmail(anyString());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Keycloak ID 로 없고 토큰에 email 도 없으면 사용자 없음이다")
    void getOrCreateProfile_MissingWithoutEmail_NotFound() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userService.getOrCreateProfile(keycloakId, null, null))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.USER_NOT_FOUND.getMessage());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Keycloak ID 로 없고 그 email 을 다른 사용자가 쓰면 잇지 않고 중복으로 막는다")
    void getOrCreateProfile_MissingEmailTakenByOther_Conflict() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.empty());
      given(userRepository.existsByEmail(testEmail)).willReturn(true);

      assertThatThrownBy(() -> userService.getOrCreateProfile(keycloakId, testEmail, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_ALREADY_EXISTS.getMessage());
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("계정 삭제 테스트")
  class DeleteAccountTests {

    @Test
    @DisplayName("정상적으로 계정이 삭제된다")
    void deleteAccount_Success() {
      // given
      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setAccounts(new ArrayList<>());

      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
      given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(new ArrayList<>());
      doNothing().when(keycloakService).deleteUser(keycloakId);

      // when
      userService.deleteAccount(keycloakId, testPassword, null);

      // then
      verify(emailTokenRepository).deleteByUser(testUser);
      verify(keycloakService).deleteUser(keycloakId);
      verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 예외가 발생한다")
    void deleteAccount_InvalidPassword_ThrowsException() {
      // given
      String wrongPassword = "wrongPassword";
      testUser.setPasswordHash("$2a$10$encodedPassword");

      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).willReturn(false);

      // when & then
      assertThatThrownBy(() ->
        userService.deleteAccount(keycloakId, wrongPassword, null)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_PASSWORD.getMessage());

      verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("잔액이 있어도 삭제되고, 계좌마다 삭제 이벤트를 보낸다")
    void deleteAccount_WithBalance_DeletesAndPublishesPerAccount() {
      Account first = Account.builder().id(1L).user(testUser).balanceKrw(new BigDecimal("1000000")).balanceUsd(BigDecimal.ZERO).build();
      Account second = Account.builder().id(2L).user(testUser).balanceKrw(BigDecimal.ZERO).balanceUsd(new BigDecimal("12.5")).build();
      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
      given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(Arrays.asList(first, second));

      userService.deleteAccount(keycloakId, testPassword, null);

      verify(accountEventProducer).publishAccountDeleted(first, "회원 탈퇴");
      verify(accountEventProducer).publishAccountDeleted(second, "회원 탈퇴");
      verify(accountRepository).deleteAll(Arrays.asList(first, second));
      verify(passwordResetTokenRepository).deleteByUser(testUser);
      verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("비밀번호가 있는 사용자가 비밀번호 없이 보내면 거절한다")
    void deleteAccount_PasswordUserWithoutPassword_Rejected() {
      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));

      assertThatThrownBy(() -> userService.deleteAccount(keycloakId, null, Instant.now()))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_PASSWORD.getMessage());
      verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("비밀번호 없는 사용자(구글)는 방금 로그인한 토큰이면 지운다")
    void deleteAccount_SocialUserRecentLogin_Deletes() {
      testUser.setPasswordHash(null);
      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));
      given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(new ArrayList<>());

      userService.deleteAccount(keycloakId, null, Instant.now().minus(Duration.ofMinutes(1)));

      verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("비밀번호 없는 사용자(구글)는 로그인한 지 오래됐거나 시각이 없으면 다시 로그인하라고 한다")
    void deleteAccount_SocialUserStaleLogin_RequiresReauth() {
      testUser.setPasswordHash(null);
      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));

      for (Instant authTime : Arrays.asList(Instant.now().minus(Duration.ofMinutes(6)), null)) {
        assertThatThrownBy(() -> userService.deleteAccount(keycloakId, null, authTime))
          .isInstanceOf(UserException.class)
          .hasMessage(UserResponse.REAUTH_REQUIRED.getMessage());
      }
      verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("Keycloak 삭제 실패 시에도 로컬 계정은 삭제된다")
    void deleteAccount_KeycloakDeleteFailure_StillDeletesLocal() {
      // given
      testUser.setPasswordHash("$2a$10$encodedPassword");
      testUser.setAccounts(new ArrayList<>());

      given(userRepository.findByKeycloakIdWithAccounts(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
      given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(new ArrayList<>());
      doThrow(new RuntimeException("Keycloak unavailable"))
        .when(keycloakService).deleteUser(keycloakId);

      // when
      userService.deleteAccount(keycloakId, testPassword, null);

      // then
      verify(userRepository).delete(testUser);
    }
  }

  @Nested
  @DisplayName("프로필 수정 테스트")
  class UpdateProfileTests {

    @Test
    @DisplayName("정상적으로 닉네임이 변경된다")
    void updateProfile_Success() {
      // given
      String newNickname = "NewNickname";
      UpdateProfileRequestDto request = new UpdateProfileRequestDto();
      request.setNickname(newNickname);

      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(userRepository.existsByNickname(newNickname)).willReturn(false);
      given(userRepository.save(testUser)).willReturn(testUser);

      // when
      User result = userService.updateProfile(keycloakId, request);

      // then
      assertThat(result.getNickname()).isEqualTo(newNickname);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("중복된 닉네임이면 예외가 발생한다")
    void updateProfile_DuplicateNickname_ThrowsException() {
      // given
      String duplicateNickname = "ExistingNickname";
      UpdateProfileRequestDto request = new UpdateProfileRequestDto();
      request.setNickname(duplicateNickname);

      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(userRepository.existsByNickname(duplicateNickname)).willReturn(true);

      // when & then
      assertThatThrownBy(() ->
        userService.updateProfile(keycloakId, request)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.NICKNAME_ALREADY_EXISTS.getMessage());

      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("동일한 닉네임으로 변경하는 것은 허용된다")
    void updateProfile_SameNickname_Success() {
      // given
      UpdateProfileRequestDto request = new UpdateProfileRequestDto();
      request.setNickname(testNickname); // Same as current

      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(userRepository.existsByNickname(testNickname)).willReturn(true);
      given(userRepository.save(testUser)).willReturn(testUser);

      // when
      User result = userService.updateProfile(keycloakId, request);

      // then
      assertThat(result.getNickname()).isEqualTo(testNickname);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("닉네임이 null이면 변경되지 않는다")
    void updateProfile_NullNickname_NoChange() {
      // given
      UpdateProfileRequestDto request = new UpdateProfileRequestDto();
      request.setNickname(null);

      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);

      // when
      User result = userService.updateProfile(keycloakId, request);

      // then
      assertThat(result.getNickname()).isEqualTo(testNickname);
      verify(userRepository, never()).existsByNickname(anyString());
      verify(userRepository).save(testUser);
    }
  }

  @Nested
  @DisplayName("이메일 인증 테스트")
  class EmailVerificationTests {

    @Test
    @DisplayName("정상적으로 이메일이 인증된다")
    void verifyEmail_Success() {
      // given
      String token = UUID.randomUUID().toString();
      EmailToken emailToken = new EmailToken();
      emailToken.setToken(token);
      emailToken.setUser(testUser);
      emailToken.setExpiryDate(LocalDateTime.now().plusHours(24));

      testUser.setEmailVerified(false);

      given(emailTokenRepository.findByTokenWithUser(token)).willReturn(Optional.of(emailToken));
      given(userRepository.save(testUser)).willReturn(testUser);

      // when
      User result = userService.verifyEmail(token);

      // then
      assertThat(result.isEmailVerified()).isTrue();
      verify(emailTokenRepository).delete(emailToken);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 예외가 발생한다")
    void verifyEmail_InvalidToken_ThrowsException() {
      // given
      String invalidToken = "invalid-token";
      given(emailTokenRepository.findByTokenWithUser(invalidToken)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
        userService.verifyEmail(invalidToken)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_TOKEN_INVALID.getMessage());

      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("만료된 토큰이면 예외가 발생한다")
    void verifyEmail_ExpiredToken_ThrowsException() {
      // given
      String token = UUID.randomUUID().toString();
      EmailToken emailToken = new EmailToken();
      emailToken.setToken(token);
      emailToken.setUser(testUser);
      emailToken.setExpiryDate(LocalDateTime.now().minusHours(1)); // Expired

      given(emailTokenRepository.findByTokenWithUser(token)).willReturn(Optional.of(emailToken));

      // when & then
      assertThatThrownBy(() ->
        userService.verifyEmail(token)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_TOKEN_EXPIRED.getMessage());

      verify(userRepository, never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("이메일 재발송 테스트")
  class ResendVerificationEmailTests {

    @Test
    @DisplayName("정상적으로 인증 이메일이 재발송된다")
    void resendVerificationEmail_Success() {
      // given
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      testUser.setEmailVerified(false);

      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(emailTokenRepository.save(any(EmailToken.class))).willReturn(new EmailToken());

      // when
      userService.resendVerificationEmail(testEmail);

      // then
      verify(emailTokenRepository).deleteByUser(testUser);
      verify(emailTokenRepository).save(any(EmailToken.class));
      verify(mailService).sendVerificationEmail(eq(testEmail), anyString());
    }

    @Test
    @DisplayName("이미 인증된 사용자면 같은 응답으로 끝내고 메일을 보내지 않는다(인증 여부를 드러내지 않는다)")
    void resendVerificationEmail_AlreadyVerified_SilentlyEnds() {
      // given
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      testUser.setEmailVerified(true);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

      // when
      userService.resendVerificationEmail(testEmail);

      // then
      verify(emailTokenRepository, never()).save(any(EmailToken.class));
      verify(mailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("없는 사용자여도 같은 응답으로 끝내고 메일을 보내지 않는다(가입 여부를 드러내지 않는다)")
    void resendVerificationEmail_UserNotFound_SilentlyEnds() {
      // given
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.empty());

      // when
      userService.resendVerificationEmail(testEmail);

      // then
      verify(mailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("이메일 토큰 생성 시 기존 토큰이 삭제된다")
    void resendVerificationEmail_DeletesExistingToken() {
      // given
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      testUser.setEmailVerified(false);

      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(emailTokenRepository.save(any(EmailToken.class))).willReturn(new EmailToken());

      // when
      userService.resendVerificationEmail(testEmail);

      // then
      verify(emailTokenRepository).deleteByUser(testUser);
    }
  }

  @Nested
  @DisplayName("관리자 API 테스트")
  class AdminApiTests {

    @Test
    @DisplayName("모든 사용자를 페이지네이션으로 조회한다")
    void getAllUsers_Success() {
      // given
      Pageable pageable = PageRequest.of(0, 10);
      List<User> users = Arrays.asList(testUser, testUser);
      Page<User> userPage = new PageImpl<>(users, pageable, 2);

      given(userRepository.findAll(pageable)).willReturn(userPage);

      // when
      Page<User> result = userService.getAllUsers(pageable);

      // then
      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getTotalElements()).isEqualTo(2);
      verify(userRepository).findAll(pageable);
    }

    @Test
    @DisplayName("사용자 ID로 조회한다")
    void findById_Success() {
      // given
      Long userId = 1L;
      given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

      // when
      User result = userService.findById(userId);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(userId);
      verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 예외가 발생한다")
    void findById_NotFound_ThrowsException() {
      // given
      Long userId = 999L;
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
        userService.findById(userId)
      ).isInstanceOf(UserException.class)
        .hasMessage(UserResponse.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("관리자로 승격하면 Keycloak admin 역할을 부여한 뒤 저장한다")
    void updateUserRole_Promote_AssignsThenSaves() {
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);

      User result = userService.updateUserRole(1L, UserRole.ADMIN, "kc-admin");

      assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
      InOrder order = inOrder(keycloakService, userRepository);
      order.verify(keycloakService).assignRealmRole(keycloakId, "admin");
      order.verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("사용자로 강등하면 admin 역할을 해제하고 세션을 종료한 뒤 저장한다")
    void updateUserRole_Demote_RemovesLogsOutThenSaves() {
      testUser.changeRole(UserRole.ADMIN);
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);

      User result = userService.updateUserRole(1L, UserRole.USER, "kc-admin");

      assertThat(result.getRole()).isEqualTo(UserRole.USER);
      InOrder order = inOrder(keycloakService, userRepository);
      order.verify(keycloakService).removeRealmRole(keycloakId, "admin");
      order.verify(keycloakService).logoutUserSessions(keycloakId);
      order.verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Keycloak 역할 해제 실패 시 DB 역할을 바꾸지 않는다")
    void updateUserRole_KeycloakFails_KeepsRole() {
      testUser.changeRole(UserRole.ADMIN);
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
      willThrow(new KeycloakException(KeycloakResponse.API_ERROR)).given(keycloakService).removeRealmRole(keycloakId, "admin");

      assertThatThrownBy(() -> userService.updateUserRole(1L, UserRole.USER, "kc-admin"))
        .isInstanceOf(KeycloakException.class);
      assertThat(testUser.getRole()).isEqualTo(UserRole.ADMIN);
      verify(keycloakService, never()).logoutUserSessions(anyString());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("세션 종료 실패 시 DB 역할을 바꾸지 않는다")
    void updateUserRole_LogoutFails_KeepsRole() {
      testUser.changeRole(UserRole.ADMIN);
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
      willThrow(new KeycloakException(KeycloakResponse.API_ERROR)).given(keycloakService).logoutUserSessions(keycloakId);

      assertThatThrownBy(() -> userService.updateUserRole(1L, UserRole.USER, "kc-admin"))
        .isInstanceOf(KeycloakException.class);
      assertThat(testUser.getRole()).isEqualTo(UserRole.ADMIN);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자는 자기 역할을 바꿀 수 없다")
    void updateUserRole_Self_Rejected() {
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

      assertThatThrownBy(() -> userService.updateUserRole(1L, UserRole.USER, keycloakId))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.CANNOT_CHANGE_OWN_ROLE.getMessage());
      verifyNoInteractions(keycloakService);
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자 활성화 상태를 변경한다")
    void updateUserEnabled_Success() {
      // given
      Long userId = 1L;
      boolean newStatus = false;

      given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);

      // when
      User result = userService.updateUserEnabled(userId, newStatus);

      // then
      assertThat(result.isEnabled()).isFalse();
      verify(keycloakService).setEnabled(keycloakId, false);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Keycloak 활성 상태 변경이 실패하면 로컬도 그대로 둔다")
    void updateUserEnabled_KeycloakFailure_KeepsLocal() {
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
      doThrow(new RuntimeException("keycloak down")).when(keycloakService).setEnabled(keycloakId, false);

      assertThatThrownBy(() -> userService.updateUserEnabled(1L, false))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());

      assertThat(testUser.isEnabled()).isTrue();
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("전체 사용자 수를 조회한다")
    void countTotalUsers_Success() {
      // given
      given(userRepository.count()).willReturn(100L);

      // when
      long result = userService.countTotalUsers();

      // then
      assertThat(result).isEqualTo(100L);
      verify(userRepository).count();
    }

    @Test
    @DisplayName("활성 사용자 수를 조회한다")
    void countActiveUsers_Success() {
      // given
      given(userRepository.countByEnabled(true)).willReturn(85L);

      // when
      long result = userService.countActiveUsers();

      // then
      assertThat(result).isEqualTo(85L);
      verify(userRepository).countByEnabled(true);
    }

    @Test
    @DisplayName("관리자 수를 조회한다")
    void countAdminUsers_Success() {
      // given
      given(userRepository.countByRole(UserRole.ADMIN)).willReturn(5L);

      // when
      long result = userService.countAdminUsers();

      // then
      assertThat(result).isEqualTo(5L);
      verify(userRepository).countByRole(UserRole.ADMIN);
    }

    @Test
    @DisplayName("관리자가 사용자 닉네임을 변경한다")
    void adminUpdateNickname_Success() {
      // given
      Long userId = 1L;
      String newNickname = "AdminChanged";

      given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);

      // when
      User result = userService.adminUpdateNickname(userId, newNickname);

      // then
      assertThat(result.getNickname()).isEqualTo(newNickname);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("관리자가 사용자 비밀번호를 변경한다")
    void adminUpdatePassword_Success() {
      // given
      Long userId = 1L;
      String newPassword = "newPassword123";

      given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);
      given(passwordEncoder.encode(newPassword)).willReturn("$2a$10$encodedNewPassword");
      willDoNothing().given(keycloakService).resetPassword(keycloakId, newPassword);

      // when
      userService.adminUpdatePassword(userId, newPassword);

      // then
      verify(keycloakService).resetPassword(keycloakId, newPassword);
      verify(passwordEncoder).encode(newPassword);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("관리자가 사용자 이메일을 강제 인증한다")
    void adminVerifyEmail_Success() {
      // given
      Long userId = 1L;
      testUser.setEmailVerified(false);

      given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
      given(userRepository.save(testUser)).willReturn(testUser);
      willDoNothing().given(emailTokenRepository).deleteByUser(testUser);

      // when
      User result = userService.adminVerifyEmail(userId);

      // then
      assertThat(result.isEmailVerified()).isTrue();
      verify(emailTokenRepository).deleteByUser(testUser);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("관리자가 사용자를 강제 탈퇴시킨다")
    void adminDeleteUser_Success() {
      // given
      Long userId = 1L;

      given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
      Account account = Account.builder().id(7L).user(testUser).balanceKrw(new BigDecimal("1000000")).balanceUsd(BigDecimal.ZERO).build();
      given(accountRepository.findByUserIdWithUser(userId)).willReturn(List.of(account));
      willDoNothing().given(emailTokenRepository).deleteByUser(testUser);
      willDoNothing().given(passwordResetTokenRepository).deleteByUser(testUser);
      willDoNothing().given(accountRepository).deleteAll(any());
      willDoNothing().given(keycloakService).deleteUser(keycloakId);
      willDoNothing().given(userRepository).delete(testUser);

      // when
      userService.adminDeleteUser(userId);

      // then
      verify(emailTokenRepository).deleteByUser(testUser);
      verify(passwordResetTokenRepository).deleteByUser(testUser);
      verify(accountRepository).findByUserIdWithUser(userId);
      verify(accountEventProducer).publishAccountDeleted(account, "회원 탈퇴");
      verify(keycloakService).deleteUser(keycloakId);
      verify(userRepository).delete(testUser);
    }
  }

  @Nested
  @DisplayName("Keycloak 사용자 동기화 테스트")
  class CreateUserFromKeycloakTests {

    @Test
    @DisplayName("Keycloak OAuth 사용자를 생성한다")
    void createUserFromKeycloak_Success() {
      // given
      String keycloakId = "keycloak-123";
      String email = "oauth@example.com";
      String nickname = "oauthUser";

      User oauthUser = User.builder()
        .id(1L)
        .keycloakId(keycloakId)
        .email(email)
        .nickname(nickname)
        .build();

      given(userRepository.existsByEmail(email)).willReturn(false);
      given(userMapper.createLocalUser(email, nickname, keycloakId)).willReturn(oauthUser);
      given(userRepository.save(any(User.class))).willReturn(oauthUser);

      // when
      User result = userService.createUserFromKeycloak(keycloakId, email, nickname);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getEmail()).isEqualTo(email);
      assertThat(result.getKeycloakId()).isEqualTo(keycloakId);
      verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 예외가 발생한다")
    void createUserFromKeycloak_DuplicateEmail_ThrowsException() {
      // given
      String email = "existing@example.com";
      given(userRepository.existsByEmail(email)).willReturn(true);

      // when & then
      assertThatThrownBy(() ->
        userService.createUserFromKeycloak("keycloak-123", email, "nickname"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.EMAIL_ALREADY_EXISTS.getMessage());
    }
  }

  @Nested
  @DisplayName("비밀번호 변경 테스트")
  class ChangePasswordTests {

    @Test
    @DisplayName("현재 비밀번호가 맞으면 Keycloak 과 로컬 해시를 같이 바꾼다")
    void changePassword_UpdatesKeycloakAndLocalHash() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, "$2a$10$encodedPassword")).willReturn(true);
      given(passwordEncoder.encode("newPassw0rd!")).willReturn("$2a$10$encodedNewPassword");

      userService.changePassword(keycloakId, testPassword, "newPassw0rd!");

      verify(keycloakService).resetPassword(keycloakId, "newPassw0rd!");
      assertThat(testUser.getPasswordHash()).isEqualTo("$2a$10$encodedNewPassword");
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 으로 막고 아무것도 바꾸지 않는다")
    void changePassword_WrongCurrent_Rejected() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches("wrongPassword", "$2a$10$encodedPassword")).willReturn(false);

      assertThatThrownBy(() -> userService.changePassword(keycloakId, "wrongPassword", "newPassw0rd!"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_PASSWORD.getMessage());

      verify(keycloakService, never()).resetPassword(anyString(), anyString());
      assertThat(testUser.getPasswordHash()).isEqualTo("$2a$10$encodedPassword");
    }

    @Test
    @DisplayName("비밀번호가 없는 소셜 사용자는 대조 없이 같은 응답으로 막는다")
    void changePassword_SocialUser_Rejected() {
      testUser.setPasswordHash(null);
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));

      assertThatThrownBy(() -> userService.changePassword(keycloakId, "anything", "newPassw0rd!"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INVALID_PASSWORD.getMessage());

      verify(passwordEncoder, never()).matches(any(), any());
      verify(keycloakService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("Keycloak 변경이 실패하면 로컬 해시도 그대로 둔다")
    void changePassword_KeycloakFailure_KeepsLocalHash() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, "$2a$10$encodedPassword")).willReturn(true);
      doThrow(new RuntimeException("keycloak down")).when(keycloakService).resetPassword(keycloakId, "newPassw0rd!");

      assertThatThrownBy(() -> userService.changePassword(keycloakId, testPassword, "newPassw0rd!"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.PASSWORD_CHANGE_FAILED.getMessage());

      assertThat(testUser.getPasswordHash()).isEqualTo("$2a$10$encodedPassword");
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("비밀번호 재설정 테스트")
  class PasswordResetTests {

    @Test
    @DisplayName("비밀번호 재설정을 요청한다")
    void requestPasswordReset_Success() {
      // given
      testUser.setEmailVerified(true);
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

      // when
      userService.requestPasswordReset(testEmail);

      // then
      verify(userRepository).findByEmail(testEmail);
      verify(rateLimitService).tryAcquire(testEmail);
    }

    @Test
    @DisplayName("이메일 미인증 사용자는 같은 응답으로 끝내고 재설정 메일을 보내지 않는다")
    void requestPasswordReset_UnverifiedEmail_SilentlyEnds() {
      // given
      testUser.setEmailVerified(false);
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

      // when
      userService.requestPasswordReset(testEmail);

      // then
      verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
      verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Rate limit 초과 시 예외 발생")
    void requestPasswordReset_RateLimitExceeded_ThrowsException() {
      // given
      given(rateLimitService.tryAcquire(testEmail)).willReturn(false);
      given(rateLimitService.getRemainingWaitSeconds(testEmail)).willReturn(30L);

      // when & then
      assertThatThrownBy(() -> userService.requestPasswordReset(testEmail))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.RATE_LIMIT_EXCEEDED.getMessage());
    }

    @Test
    @DisplayName("유효한 토큰으로 비밀번호를 재설정한다")
    void resetPassword_Success() {
      // given
      String token = "valid-reset-token";
      String newPassword = "newPassword123";

      PasswordResetToken resetToken = new PasswordResetToken();
      resetToken.setToken(token);
      resetToken.setUser(testUser);
      resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(30));
      resetToken.setUsed(false);

      given(passwordResetTokenRepository.findByTokenWithUser(token)).willReturn(
        Optional.of(resetToken));
      given(passwordEncoder.encode(newPassword)).willReturn("$2a$10$encodedNewPassword");
      given(passwordResetTokenRepository.save(resetToken)).willReturn(resetToken);
      given(userRepository.save(testUser)).willReturn(testUser);
      willDoNothing().given(keycloakService).resetPassword(keycloakId, newPassword);

      // when
      userService.resetPassword(token, newPassword);

      // then
      verify(keycloakService).resetPassword(keycloakId, newPassword);
      verify(passwordEncoder).encode(newPassword);
      verify(userRepository).save(testUser);
      verify(passwordResetTokenRepository).save(resetToken);
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 재설정 시 예외 발생")
    void resetPassword_InvalidToken_ThrowsException() {
      // given
      String invalidToken = "invalid-token";
      given(passwordResetTokenRepository.findByTokenWithUser(invalidToken)).willReturn(
        Optional.empty());

      // when & then
      assertThatThrownBy(() -> userService.resetPassword(invalidToken, "newPassword"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.PASSWORD_RESET_TOKEN_INVALID.getMessage());
    }

    @Test
    @DisplayName("만료된 토큰으로 재설정 시 예외 발생")
    void resetPassword_ExpiredToken_ThrowsException() {
      // given
      String token = "expired-token";

      PasswordResetToken resetToken = new PasswordResetToken();
      resetToken.setToken(token);
      resetToken.setUser(testUser);
      resetToken.setExpiryDate(LocalDateTime.now().minusMinutes(1)); // 만료됨
      resetToken.setUsed(false);

      given(passwordResetTokenRepository.findByTokenWithUser(token)).willReturn(
        Optional.of(resetToken));

      // when & then
      assertThatThrownBy(() -> userService.resetPassword(token, "newPassword"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.PASSWORD_RESET_TOKEN_EXPIRED.getMessage());
    }

    @Test
    @DisplayName("이미 사용된 토큰으로 재설정 시 예외 발생")
    void resetPassword_UsedToken_ThrowsException() {
      // given
      String token = "used-token";

      PasswordResetToken resetToken = new PasswordResetToken();
      resetToken.setToken(token);
      resetToken.setUser(testUser);
      resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(30));
      resetToken.setUsed(true); // 이미 사용됨

      given(passwordResetTokenRepository.findByTokenWithUser(token)).willReturn(
        Optional.of(resetToken));

      // when & then
      assertThatThrownBy(() -> userService.resetPassword(token, "newPassword"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.PASSWORD_RESET_TOKEN_EXPIRED.getMessage());
    }
  }

  @Nested
  @DisplayName("실패 경로")
  class FailureTests {

    private PasswordResetToken validResetToken(String token) {
      PasswordResetToken resetToken = new PasswordResetToken();
      resetToken.setToken(token);
      resetToken.setUser(testUser);
      resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(30));
      resetToken.setUsed(false);
      return resetToken;
    }

    @Test
    @DisplayName("가입 중 Keycloak 에서 예상하지 못한 예외가 나면 INTERNAL_SERVER_ERROR 이고 저장하지 않는다")
    void register_UnexpectedKeycloakError() {
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, testPassword)).willThrow(new IllegalStateException("connection reset"));

      assertThatThrownBy(() -> userService.registerUser(testEmail, testPassword, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Keycloak 생성 뒤 로컬 저장이 실패하면 INTERNAL_SERVER_ERROR 이고 인증 메일을 보내지 않는다")
    void register_LocalSaveFails() {
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(keycloakService.createUser(testEmail, testPassword)).willReturn(keycloakId);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(testUser);
      given(passwordEncoder.encode(testPassword)).willReturn("$2a$10$encoded");
      given(userRepository.save(testUser)).willThrow(new IllegalStateException("db down"));

      assertThatThrownBy(() -> userService.registerUser(testEmail, testPassword, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
      verify(mailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("구글 사용자 동기화 저장이 실패하면 INTERNAL_SERVER_ERROR 다")
    void createUserFromKeycloak_SaveFails() {
      given(userRepository.existsByEmail(testEmail)).willReturn(false);
      given(userMapper.createLocalUser(testEmail, testNickname, keycloakId)).willReturn(testUser);
      given(userRepository.save(testUser)).willThrow(new IllegalStateException("db down"));

      assertThatThrownBy(() -> userService.createUserFromKeycloak(keycloakId, testEmail, testNickname))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
    }

    @Test
    @DisplayName("비밀번호가 맞아도 Keycloak 로그인이 실패하면 실패 사유 KEYCLOAK_ERROR 를 발행하고 그대로 올린다")
    void login_KeycloakError_PublishesAndRethrows() {
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
      KeycloakException failure = new KeycloakException(KeycloakResponse.API_ERROR);
      given(keycloakService.login(testEmail, testPassword)).willThrow(failure);

      assertThatThrownBy(() -> userService.login(testEmail, testPassword)).isSameAs(failure);
      verify(loginEventProducer).publishLoginFailed(eq(testEmail), eq("KEYCLOAK_ERROR"), anyString(),
        eq("PASSWORD"), any(), any());
    }

    @Test
    @DisplayName("없는 사용자의 프로필은 고치지 않는다")
    void updateProfile_UserNotFound() {
      given(userRepository.findByKeycloakId(keycloakId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userService.updateProfile(keycloakId, new UpdateProfileRequestDto()))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.USER_NOT_FOUND.getMessage());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("인증 메일 재발송이 1분 안에 다시 오면 사용자를 찾지 않고 막는다")
    void resendVerification_RateLimited() {
      given(rateLimitService.tryAcquire(testEmail)).willReturn(false);
      given(rateLimitService.getRemainingWaitSeconds(testEmail)).willReturn(42L);

      assertThatThrownBy(() -> userService.resendVerificationEmail(testEmail))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.RATE_LIMIT_EXCEEDED.getMessage());
      verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("없는 이메일로 비밀번호 재설정을 요청해도 같은 응답으로 끝내고 메일을 보내지 않는다")
    void requestPasswordReset_UserNotFound_SilentlyEnds() {
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.empty());

      userService.requestPasswordReset(testEmail);

      verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
      verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("재설정 메일 발송이 실패해도 요청은 성공으로 끝난다(토큰은 저장된다)")
    void requestPasswordReset_MailFails_StillSucceeds() {
      given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
      given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
      doThrow(new IllegalStateException("smtp down")).when(mailService).sendPasswordResetEmail(eq(testEmail), anyString());

      userService.requestPasswordReset(testEmail);

      verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Keycloak 비밀번호 재설정이 실패하면 로컬 비밀번호와 토큰을 그대로 둔다")
    void resetPassword_KeycloakFails_KeepsLocalState() {
      given(passwordResetTokenRepository.findByTokenWithUser("reset-token")).willReturn(Optional.of(validResetToken("reset-token")));
      doThrow(new KeycloakException(KeycloakResponse.API_ERROR)).when(keycloakService).resetPassword(keycloakId, "N3w-pass!");

      assertThatThrownBy(() -> userService.resetPassword("reset-token", "N3w-pass!"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.PASSWORD_RESET_FAILED.getMessage());
      assertThat(testUser.getPasswordHash()).isEqualTo("$2a$10$encodedPassword");
      verify(userRepository, never()).save(any());
      verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자 비밀번호 변경도 Keycloak 이 실패하면 로컬 비밀번호를 바꾸지 않는다")
    void adminUpdatePassword_KeycloakFails() {
      given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
      doThrow(new KeycloakException(KeycloakResponse.API_ERROR)).when(keycloakService).resetPassword(keycloakId, "N3w-pass!");

      assertThatThrownBy(() -> userService.adminUpdatePassword(1L, "N3w-pass!"))
        .isInstanceOf(UserException.class)
        .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
      assertThat(testUser.getPasswordHash()).isEqualTo("$2a$10$encodedPassword");
      verify(userRepository, never()).save(any());
    }
  }
}
