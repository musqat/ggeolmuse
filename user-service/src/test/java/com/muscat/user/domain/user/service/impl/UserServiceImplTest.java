package com.muscat.user.domain.user.service.impl;

import com.muscat.user.common.enums.responses.KeycloakResponse;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.util.RateLimitService;
import com.muscat.user.config.mail.MailService;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.repository.EmailTokenRepository;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

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
        @DisplayName("정상적으로 로그인이 성공한다")
        void login_Success() {
            // given
            String jwtToken = "jwt.token.here";
            String rawPassword = "password123";

            testUser.setPasswordHash("$2a$10$encodedPassword");
            testUser.setEmailVerified(true);

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches(rawPassword, testUser.getPasswordHash())).willReturn(true);
            given(keycloakService.login(testEmail, rawPassword)).willReturn(jwtToken);

            // when
            String result = userService.login(testEmail, rawPassword);

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
        @DisplayName("이메일 미인증 사용자는 로그인할 수 없다")
        void login_EmailNotVerified_ThrowsException() {
            // given
            testUser.setEmailVerified(false);
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() ->
                    userService.login(testEmail, testPassword)
            ).isInstanceOf(UserException.class)
             .hasMessage(UserResponse.EMAIL_NOT_VERIFIED.getMessage());

            verify(passwordEncoder, never()).matches(anyString(), anyString());
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
    @DisplayName("프로필 조회 테스트")
    class GetProfileTests {

        @Test
        @DisplayName("정상적으로 프로필을 조회한다")
        void getProfile_Success() {
            // given
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

            // when
            User result = userService.getProfile(testEmail);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(testEmail);
            assertThat(result.getNickname()).isEqualTo(testNickname);
            verify(userRepository).findByEmail(testEmail);
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 예외가 발생한다")
        void getProfile_UserNotFound_ThrowsException() {
            // given
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    userService.getProfile(testEmail)
            ).isInstanceOf(UserException.class)
             .hasMessage(UserResponse.USER_NOT_FOUND.getMessage());
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

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
            given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(new ArrayList<>());
            doNothing().when(keycloakService).deleteUser(keycloakId);

            // when
            userService.deleteAccount(testEmail, testPassword);

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

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).willReturn(false);

            // when & then
            assertThatThrownBy(() ->
                    userService.deleteAccount(testEmail, wrongPassword)
            ).isInstanceOf(UserException.class)
             .hasMessage(UserResponse.INVALID_PASSWORD.getMessage());

            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("잔액이 있는 계좌가 있으면 삭제할 수 없다")
        void deleteAccount_HasBalance_ThrowsException() {
            // given
            testUser.setPasswordHash("$2a$10$encodedPassword");

            Account accountWithBalance = Account.builder()
                    .id(1L)
                    .user(testUser)
                    .balanceKrw(new BigDecimal("1000000"))
                    .balanceUsd(BigDecimal.ZERO)
                    .build();

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
            given(accountRepository.findByUserIdWithUser(testUser.getId()))
                    .willReturn(Arrays.asList(accountWithBalance));

            // when & then
            assertThatThrownBy(() ->
                    userService.deleteAccount(testEmail, testPassword)
            ).isInstanceOf(UserException.class)
             .hasMessage(UserResponse.ACCOUNT_DELETION_BLOCKED.getMessage());

            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("Keycloak 삭제 실패 시에도 로컬 계정은 삭제된다")
        void deleteAccount_KeycloakDeleteFailure_StillDeletesLocal() {
            // given
            testUser.setPasswordHash("$2a$10$encodedPassword");
            testUser.setAccounts(new ArrayList<>());

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
            given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(new ArrayList<>());
            doThrow(new RuntimeException("Keycloak unavailable"))
                    .when(keycloakService).deleteUser(keycloakId);

            // when
            userService.deleteAccount(testEmail, testPassword);

            // then
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Keycloak ID가 없어도 로컬 계정은 삭제된다")
        void deleteAccount_NoKeycloakId_StillDeletesLocal() {
            // given
            testUser.setPasswordHash("$2a$10$encodedPassword");
            testUser.setKeycloakId(null);
            testUser.setAccounts(new ArrayList<>());

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches(testPassword, testUser.getPasswordHash())).willReturn(true);
            given(accountRepository.findByUserIdWithUser(testUser.getId())).willReturn(new ArrayList<>());

            // when
            userService.deleteAccount(testEmail, testPassword);

            // then
            verify(keycloakService, never()).deleteUser(anyString());
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

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(userRepository.existsByNickname(newNickname)).willReturn(false);
            given(userRepository.save(testUser)).willReturn(testUser);

            // when
            User result = userService.updateProfile(testEmail, request);

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

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(userRepository.existsByNickname(duplicateNickname)).willReturn(true);

            // when & then
            assertThatThrownBy(() ->
                    userService.updateProfile(testEmail, request)
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

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(userRepository.existsByNickname(testNickname)).willReturn(true);
            given(userRepository.save(testUser)).willReturn(testUser);

            // when
            User result = userService.updateProfile(testEmail, request);

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

            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));
            given(userRepository.save(testUser)).willReturn(testUser);

            // when
            User result = userService.updateProfile(testEmail, request);

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

            given(emailTokenRepository.findByToken(token)).willReturn(Optional.of(emailToken));
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
            given(emailTokenRepository.findByToken(invalidToken)).willReturn(Optional.empty());

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

            given(emailTokenRepository.findByToken(token)).willReturn(Optional.of(emailToken));

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
        @DisplayName("이미 인증된 사용자는 재발송할 수 없다")
        void resendVerificationEmail_AlreadyVerified_ThrowsException() {
            // given
            given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
            testUser.setEmailVerified(true);
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() ->
                    userService.resendVerificationEmail(testEmail)
            ).isInstanceOf(UserException.class)
             .hasMessage(UserResponse.EMAIL_ALREADY_VERIFIED.getMessage());

            verify(emailTokenRepository, never()).save(any(EmailToken.class));
            verify(mailService, never()).sendVerificationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 예외가 발생한다")
        void resendVerificationEmail_UserNotFound_ThrowsException() {
            // given
            given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    userService.resendVerificationEmail(testEmail)
            ).isInstanceOf(UserException.class)
             .hasMessage(UserResponse.USER_NOT_FOUND.getMessage());
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
        @DisplayName("사용자 역할을 변경한다")
        void updateUserRole_Success() {
            // given
            Long userId = 1L;
            UserRole newRole = UserRole.ADMIN;

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            given(userRepository.save(testUser)).willReturn(testUser);

            // when
            User result = userService.updateUserRole(userId, newRole);

            // then
            assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
            verify(userRepository).save(testUser);
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
            verify(userRepository).save(testUser);
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
            given(accountRepository.findByUserIdWithUser(userId)).willReturn(List.of());
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
        @DisplayName("이메일 미인증 사용자는 비밀번호 재설정 불가")
        void requestPasswordReset_UnverifiedEmail_ThrowsException() {
            // given
            testUser.setEmailVerified(false);
            given(rateLimitService.tryAcquire(testEmail)).willReturn(true);
            given(userRepository.findByEmail(testEmail)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() -> userService.requestPasswordReset(testEmail))
                    .isInstanceOf(UserException.class)
                    .hasMessage(UserResponse.EMAIL_NOT_VERIFIED.getMessage());
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

            given(passwordResetTokenRepository.findByToken(token)).willReturn(Optional.of(resetToken));
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
            given(passwordResetTokenRepository.findByToken(invalidToken)).willReturn(Optional.empty());

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

            given(passwordResetTokenRepository.findByToken(token)).willReturn(Optional.of(resetToken));

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

            given(passwordResetTokenRepository.findByToken(token)).willReturn(Optional.of(resetToken));

            // when & then
            assertThatThrownBy(() -> userService.resetPassword(token, "newPassword"))
                    .isInstanceOf(UserException.class)
                    .hasMessage(UserResponse.PASSWORD_RESET_TOKEN_EXPIRED.getMessage());
        }
    }
}
