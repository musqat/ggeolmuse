package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muscat.user.common.exceptions.SocialLoginException;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleUserUtil 단위 테스트")
class GoogleUserUtilTest {

  @InjectMocks
  private GoogleUserUtil googleUserUtil;

  @Mock
  private UserMapper userMapper;

  @Mock
  private User mockUser;

  @Nested
  @DisplayName("Google ID 추출 테스트")
  class ExtractGoogleIdTests {

    @Test
    @DisplayName("sub 필드에서 Google ID 추출")
    void extractGoogleId_FromSub_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("sub", "google-123456789");

      // when
      String result = googleUserUtil.extractGoogleId(tokenClaims);

      // then
      assertThat(result).isEqualTo("google-123456789");
    }

    @Test
    @DisplayName("preferred_username 필드에서 Google ID 추출")
    void extractGoogleId_FromPreferredUsername_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("preferred_username", "user@example.com");

      // when
      String result = googleUserUtil.extractGoogleId(tokenClaims);

      // then
      assertThat(result).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("email 필드에서 Google ID 생성 (해시 기반)")
    void extractGoogleId_FromEmail_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("email", "test@gmail.com");
      int expectedHash = "test@gmail.com".hashCode();

      // when
      String result = googleUserUtil.extractGoogleId(tokenClaims);

      // then
      assertThat(result).isEqualTo("google_" + expectedHash);
    }

    @Test
    @DisplayName("sub가 null이고 preferred_username 사용")
    void extractGoogleId_SubNullUsesPreferredUsername() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("sub", null);
      tokenClaims.put("preferred_username", "fallback-user");

      // when
      String result = googleUserUtil.extractGoogleId(tokenClaims);

      // then
      assertThat(result).isEqualTo("fallback-user");
    }

    @Test
    @DisplayName("sub와 preferred_username이 null이면 email 사용")
    void extractGoogleId_SubAndPreferredNullUsesEmail() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("sub", null);
      tokenClaims.put("preferred_username", null);
      tokenClaims.put("email", "backup@gmail.com");
      int expectedHash = "backup@gmail.com".hashCode();

      // when
      String result = googleUserUtil.extractGoogleId(tokenClaims);

      // then
      assertThat(result).isEqualTo("google_" + expectedHash);
    }

    @Test
    @DisplayName("모든 필드가 null이면 예외 발생")
    void extractGoogleId_AllFieldsNull_ThrowsException() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();

      // when & then
      assertThatThrownBy(() -> googleUserUtil.extractGoogleId(tokenClaims))
        .isInstanceOf(SocialLoginException.class);
    }

    @Test
    @DisplayName("빈 맵이 전달되면 예외 발생")
    void extractGoogleId_EmptyMap_ThrowsException() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();

      // when & then
      assertThatThrownBy(() -> googleUserUtil.extractGoogleId(tokenClaims))
        .isInstanceOf(SocialLoginException.class);
    }
  }

  @Nested
  @DisplayName("Google 사용자 생성 테스트")
  class CreateGoogleUserFromClaimsTests {

    @Test
    @DisplayName("정상적인 Google 사용자 생성 - sub 사용")
    void createGoogleUserFromClaims_WithSub_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("sub", "google-123");
      tokenClaims.put("email", "user@gmail.com");
      tokenClaims.put("picture", "https://example.com/photo.jpg");

      String nickname = "TestUser";
      when(userMapper.createGoogleUser(
        eq("user@gmail.com"),
        eq("google-123"),
        eq(nickname),
        eq("https://example.com/photo.jpg")
      )).thenReturn(mockUser);

      // when
      User result = googleUserUtil.createGoogleUserFromClaims(tokenClaims, nickname);

      // then
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(mockUser);
      verify(userMapper).createGoogleUser(
        eq("user@gmail.com"),
        eq("google-123"),
        eq(nickname),
        eq("https://example.com/photo.jpg")
      );
    }

    @Test
    @DisplayName("picture 없이 Google 사용자 생성")
    void createGoogleUserFromClaims_NoPicture_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("sub", "google-456");
      tokenClaims.put("email", "nophoto@gmail.com");

      String nickname = "NoPhoto";
      when(userMapper.createGoogleUser(
        eq("nophoto@gmail.com"),
        eq("google-456"),
        eq(nickname),
        eq(null)
      )).thenReturn(mockUser);

      // when
      User result = googleUserUtil.createGoogleUserFromClaims(tokenClaims, nickname);

      // then
      assertThat(result).isNotNull();
      verify(userMapper).createGoogleUser(
        eq("nophoto@gmail.com"),
        eq("google-456"),
        eq(nickname),
        eq(null)
      );
    }

    @Test
    @DisplayName("preferred_username으로 Google 사용자 생성")
    void createGoogleUserFromClaims_WithPreferredUsername_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("preferred_username", "preferred-user");
      tokenClaims.put("email", "preferred@gmail.com");
      tokenClaims.put("picture", "https://example.com/avatar.png");

      String nickname = "PreferredUser";
      when(userMapper.createGoogleUser(
        eq("preferred@gmail.com"),
        eq("preferred-user"),
        eq(nickname),
        eq("https://example.com/avatar.png")
      )).thenReturn(mockUser);

      // when
      User result = googleUserUtil.createGoogleUserFromClaims(tokenClaims, nickname);

      // then
      assertThat(result).isNotNull();
      verify(userMapper).createGoogleUser(
        eq("preferred@gmail.com"),
        eq("preferred-user"),
        eq(nickname),
        eq("https://example.com/avatar.png")
      );
    }

    @Test
    @DisplayName("email 해시로 Google 사용자 생성")
    void createGoogleUserFromClaims_WithEmailHash_Success() {
      // given
      String email = "hash@gmail.com";
      int expectedHash = email.hashCode();
      String expectedGoogleId = "google_" + expectedHash;

      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("email", email);

      String nickname = "HashUser";
      when(userMapper.createGoogleUser(
        eq(email),
        eq(expectedGoogleId),
        eq(nickname),
        eq(null)
      )).thenReturn(mockUser);

      // when
      User result = googleUserUtil.createGoogleUserFromClaims(tokenClaims, nickname);

      // then
      assertThat(result).isNotNull();
      verify(userMapper).createGoogleUser(
        eq(email),
        eq(expectedGoogleId),
        eq(nickname),
        eq(null)
      );
    }

    @Test
    @DisplayName("Google ID 추출 실패 시 예외 발생")
    void createGoogleUserFromClaims_NoGoogleId_ThrowsException() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      // Google ID를 추출할 수 있는 필드가 하나도 없음

      // when & then
      assertThatThrownBy(() ->
        googleUserUtil.createGoogleUserFromClaims(tokenClaims, "TestUser"))
        .isInstanceOf(SocialLoginException.class);
    }

    @Test
    @DisplayName("빈 닉네임으로도 사용자 생성 가능")
    void createGoogleUserFromClaims_EmptyNickname_Success() {
      // given
      Map<String, Object> tokenClaims = new HashMap<>();
      tokenClaims.put("sub", "google-789");
      tokenClaims.put("email", "empty@gmail.com");

      String nickname = "";
      when(userMapper.createGoogleUser(
        eq("empty@gmail.com"),
        eq("google-789"),
        eq(nickname),
        eq(null)
      )).thenReturn(mockUser);

      // when
      User result = googleUserUtil.createGoogleUserFromClaims(tokenClaims, nickname);

      // then
      assertThat(result).isNotNull();
    }
  }
}
