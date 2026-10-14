package com.muscat.user.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
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

  private static final Jwt JWT = Jwt.withTokenValue("t")
    .header("alg", "none")
    .claim("sub", "kc-1")
    .claim("email", "g@example.local")
    .claim("name", "구글 사용자")
    .issuedAt(Instant.now())
    .expiresAt(Instant.now().plusSeconds(300))
    .build();

  @BeforeEach
  void setUp() {
    controller = new UserController(userService, userMapper);
  }

  @Test
  @DisplayName("DB 에 없는 사용자(구글 첫 로그인)는 토큰 정보로 만든다")
  void getMyProfile_NotFound_CreatesFromToken() {
    User created = User.builder().email("g@example.local").build();
    UserResponseDto dto = mock(UserResponseDto.class);
    given(userService.getProfile("g@example.local")).willThrow(new UserException(UserResponse.USER_NOT_FOUND));
    given(userService.createUserFromKeycloak("kc-1", "g@example.local", "구글 사용자")).willReturn(created);
    given(userMapper.toResponseDto(created)).willReturn(dto);

    ResponseEntity<UserResponseDto> res = controller.getMyProfile(JWT);

    assertThat(res.getBody()).isSameAs(dto);
  }

  @Test
  @DisplayName("DB 오류는 사용자를 새로 만들지 않고 그대로 올린다")
  void getMyProfile_DbError_DoesNotCreate() {
    given(userService.getProfile("g@example.local")).willThrow(new DataAccessResourceFailureException("db down"));

    assertThatThrownBy(() -> controller.getMyProfile(JWT)).isInstanceOf(DataAccessResourceFailureException.class);
    verify(userService, never()).createUserFromKeycloak(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("없는 사용자가 아닌 UserException 도 그대로 올린다")
  void getMyProfile_OtherUserException_DoesNotCreate() {
    given(userService.getProfile("g@example.local")).willThrow(new UserException(UserResponse.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> controller.getMyProfile(JWT))
      .isInstanceOf(UserException.class)
      .hasMessage(UserResponse.INTERNAL_SERVER_ERROR.getMessage());
    verify(userService, never()).createUserFromKeycloak(any(), any(), any());
  }
}
