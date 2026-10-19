package com.muscat.user.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.service.UserService;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 내 프로필")
class UserControllerProfileTest {

  @Mock
  private UserService userService;

  @Mock
  private UserMapper userMapper;

  private UserController controller;

  private static Jwt jwt(Consumer<Jwt.Builder> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("kc-1")
      .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    claims.accept(builder);
    return builder.build();
  }

  @BeforeEach
  void setUp() {
    controller = new UserController(userService, userMapper);
  }

  @Test
  @DisplayName("토큰의 sub · email · 이름으로 내 프로필을 찾거나 만든다")
  void getMyProfile_PassesSubjectEmailNickname() {
    User user = User.builder().email("g@example.local").build();
    UserResponseDto dto = mock(UserResponseDto.class);
    given(userService.getOrCreateProfile("kc-1", "g@example.local", "구글 사용자")).willReturn(user);
    given(userMapper.toResponseDto(user)).willReturn(dto);

    ResponseEntity<UserResponseDto> res =
      controller.getMyProfile(jwt(b -> b.claim("email", "g@example.local").claim("name", "구글 사용자")));

    assertThat(res.getBody()).isSameAs(dto);
  }

  @Test
  @DisplayName("닉네임은 이름, 없으면 given_name, 그것도 없으면 email 앞부분이고 email 도 없으면 비운다")
  void getMyProfile_NicknameFallbacks() {
    given(userService.getOrCreateProfile("kc-1", "g@example.local", "길동")).willReturn(User.builder().build());
    given(userService.getOrCreateProfile("kc-1", "g@example.local", "g")).willReturn(User.builder().build());
    given(userService.getOrCreateProfile("kc-1", null, null)).willReturn(User.builder().build());

    controller.getMyProfile(jwt(b -> b.claim("email", "g@example.local").claim("given_name", "길동")));
    controller.getMyProfile(jwt(b -> b.claim("email", "g@example.local")));
    controller.getMyProfile(jwt(b -> b.claim("scope", "openid")));

    verify(userService).getOrCreateProfile("kc-1", "g@example.local", "길동");
    verify(userService).getOrCreateProfile("kc-1", "g@example.local", "g");
    verify(userService).getOrCreateProfile("kc-1", null, null);
  }
}
