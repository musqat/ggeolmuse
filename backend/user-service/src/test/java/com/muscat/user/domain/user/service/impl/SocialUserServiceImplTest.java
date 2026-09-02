package com.muscat.user.domain.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.muscat.user.common.enums.responses.SocialResponse;
import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.exceptions.SocialLoginException;
import com.muscat.user.common.util.GoogleUserUtil;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.infra.kafka.LoginEventProducer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialUserService 단위 테스트")
class SocialUserServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private KeycloakService keycloakService;

  @Mock
  private GoogleUserUtil googleUserUtil;

  @Mock
  private LoginEventProducer loginEventProducer;

  @InjectMocks
  private SocialUserServiceImpl socialUserService;

  private Map<String, Object> tokenClaims;
  private String googleId;
  private String email;
  private User googleUser;

  @BeforeEach
  void setUp() {
    googleId = "google-123456789";
    email = "user@gmail.com";

    tokenClaims = new HashMap<>();
    tokenClaims.put("sub", googleId);
    tokenClaims.put("email", email);
    tokenClaims.put("picture", "https://example.com/photo.jpg");

    googleUser = User.builder()
      .id(1L)
      .email(email)
      .socialId(googleId)
      .provider(AuthType.GOOGLE)
      .nickname("Google12345")
      .profileImageUrl("https://example.com/photo.jpg")
      .emailVerified(true)
      .enabled(true)
      .build();
  }

  @Nested
  @DisplayName("Google 사용자 동기화 테스트")
  class SyncGoogleUserTests {

    @Test
    @DisplayName("기존 Google 사용자가 있으면 정보를 업데이트한다")
    void syncGoogleUser_ExistingUser_UpdatesInfo() {
      // given
      User existingUser = User.builder()
        .id(1L)
        .email("old@gmail.com")
        .socialId(googleId)
        .provider(AuthType.GOOGLE)
        .nickname("Google99999")
        .emailVerified(true)
        .enabled(true)
        .build();

      given(googleUserUtil.extractGoogleId(tokenClaims)).willReturn(googleId);
      given(userRepository.findBySocialIdAndProvider(googleId, AuthType.GOOGLE))
        .willReturn(Optional.of(existingUser));
      given(userRepository.existsByNickname(anyString())).willReturn(false);
      given(userRepository.save(any(User.class))).willReturn(existingUser);

      // when
      User result = socialUserService.syncGoogleUser(tokenClaims);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getSocialEmail()).isEqualTo(email);
      assertThat(result.getProfileImageUrl()).isEqualTo("https://example.com/photo.jpg");
      verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("새 Google 사용자를 생성한다")
    void syncGoogleUser_NewUser_CreatesUser() {
      // given
      given(googleUserUtil.extractGoogleId(tokenClaims)).willReturn(googleId);
      given(userRepository.findBySocialIdAndProvider(googleId, AuthType.GOOGLE))
        .willReturn(Optional.empty());
      given(userRepository.findByEmail(email)).willReturn(Optional.empty());
      given(userRepository.existsByNickname(anyString())).willReturn(false);
      given(googleUserUtil.createGoogleUserFromClaims(eq(tokenClaims), anyString()))
        .willReturn(googleUser);
      given(userRepository.save(googleUser)).willReturn(googleUser);

      // when
      User result = socialUserService.syncGoogleUser(tokenClaims);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getProvider()).isEqualTo(AuthType.GOOGLE);
      verify(googleUserUtil).createGoogleUserFromClaims(eq(tokenClaims), anyString());
      verify(userRepository).save(googleUser);
    }

    @Test
    @DisplayName("동일 이메일의 로컬 계정이 있으면 예외가 발생한다")
    void syncGoogleUser_EmailConflictWithLocal_ThrowsException() {
      // given
      User localUser = User.builder()
        .id(2L)
        .email(email)
        .provider(AuthType.LOCAL)
        .emailVerified(true)
        .build();

      given(googleUserUtil.extractGoogleId(tokenClaims)).willReturn(googleId);
      given(userRepository.findBySocialIdAndProvider(googleId, AuthType.GOOGLE))
        .willReturn(Optional.empty());
      given(userRepository.findByEmail(email)).willReturn(Optional.of(localUser));

      // when & then
      assertThatThrownBy(() -> socialUserService.syncGoogleUser(tokenClaims))
        .isInstanceOf(SocialLoginException.class)
        .hasMessage(SocialResponse.SOCIAL_EMAIL_CONFLICT.getMessage());
    }

    @Test
    @DisplayName("동일 이메일의 다른 Google 계정은 허용한다")
    void syncGoogleUser_SameEmailDifferentGoogleAccount_Allowed() {
      // given
      User anotherGoogleUser = User.builder()
        .id(3L)
        .email(email)
        .socialId("different-google-id")
        .provider(AuthType.GOOGLE)
        .emailVerified(true)
        .build();

      given(googleUserUtil.extractGoogleId(tokenClaims)).willReturn(googleId);
      given(userRepository.findBySocialIdAndProvider(googleId, AuthType.GOOGLE))
        .willReturn(Optional.empty());
      given(userRepository.findByEmail(email)).willReturn(Optional.of(anotherGoogleUser));
      given(userRepository.existsByNickname(anyString())).willReturn(false);
      given(googleUserUtil.createGoogleUserFromClaims(eq(tokenClaims), anyString()))
        .willReturn(googleUser);
      given(userRepository.save(googleUser)).willReturn(googleUser);

      // when
      User result = socialUserService.syncGoogleUser(tokenClaims);

      // then
      assertThat(result).isNotNull();
      verify(userRepository).save(googleUser);
    }
  }

  @Nested
  @DisplayName("고유 닉네임 생성 테스트")
  class GenerateUniqueNicknameTests {

    @Test
    @DisplayName("Google 제공자에 대해 'Google' 접두사를 사용한다")
    void generateUniqueNickname_GoogleProvider_UsesGooglePrefix() {
      // given
      given(userRepository.existsByNickname(anyString())).willReturn(false);

      // when
      String nickname = socialUserService.generateUniqueNickname(AuthType.GOOGLE);

      // then
      assertThat(nickname).startsWith("Google");
      assertThat(nickname).matches("Google\\d+");
    }

    @Test
    @DisplayName("로컬 제공자에 대해 'User' 접두사를 사용한다")
    void generateUniqueNickname_LocalProvider_UsesUserPrefix() {
      // given
      given(userRepository.existsByNickname(anyString())).willReturn(false);

      // when
      String nickname = socialUserService.generateUniqueNickname(AuthType.LOCAL);

      // then
      assertThat(nickname).startsWith("User");
      assertThat(nickname).matches("User\\d+");
    }

    @Test
    @DisplayName("중복된 닉네임이 있으면 재생성한다")
    void generateUniqueNickname_DuplicateNickname_Regenerates() {
      // given
      // 첫 번째 시도는 중복, 두 번째 시도는 성공
      given(userRepository.existsByNickname(anyString()))
        .willReturn(true)   // 첫 번째: 중복
        .willReturn(false); // 두 번째: 성공

      // when
      String nickname = socialUserService.generateUniqueNickname(AuthType.GOOGLE);

      // then
      assertThat(nickname).isNotNull();
      assertThat(nickname).startsWith("Google");
      verify(userRepository, org.mockito.Mockito.atLeast(2)).existsByNickname(anyString());
    }

    @Test
    @DisplayName("여러 번 호출해도 고유한 닉네임을 생성한다")
    void generateUniqueNickname_MultipleCalls_GeneratesUniqueNicknames() {
      // given
      given(userRepository.existsByNickname(anyString())).willReturn(false);

      // when
      String nickname1 = socialUserService.generateUniqueNickname(AuthType.GOOGLE);
      String nickname2 = socialUserService.generateUniqueNickname(AuthType.GOOGLE);

      // then
      assertThat(nickname1).isNotEmpty();
      assertThat(nickname2).isNotEmpty();
    }
  }
}
