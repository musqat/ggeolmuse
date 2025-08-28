package com.muscat.user.domain.user.controller;

import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import com.muscat.user.domain.user.dto.request.DeleteAccountRequestDto;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private final KeycloakService keycloakService;
  private final UserMapper userMapper;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponseDto>> getMyProfile(Authentication auth) {
    String email = extractEmail(auth);

    User user = userService.getProfile(email);
    UserResponseDto userDto = userMapper.toResponseDto(user);

    return ResponseEntity.ok(ApiResponse.success(UserResponse.PROFILE_FOUND, userDto));
  }

  @PutMapping("/me")
  public ResponseEntity<ApiResponse<UserResponseDto>> updateProfile(
      Authentication auth,
      @Valid @RequestBody UpdateProfileRequestDto request) {

    String email = extractEmail(auth);
    User updatedUser = userService.updateProfile(email, request);
    UserResponseDto userDto = userMapper.toResponseDto(updatedUser);

    log.info("프로필 수정 완료: {}", email);
    return ResponseEntity.ok(ApiResponse.success(UserResponse.PROFILE_UPDATED, userDto));
  }

  @PutMapping("/me/password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      Authentication auth,
      @Valid @RequestBody ChangePasswordRequestDto request) {

    String keycloakId = extractKeycloakId(auth);
    String email = extractEmail(auth);

    keycloakService.changePassword(keycloakId, request);

    log.info("비밀번호 변경 완료: {}", email);
    return ResponseEntity.ok(ApiResponse.success(UserResponse.PASSWORD_CHANGED));
  }

  @DeleteMapping("/me")
  public ResponseEntity<ApiResponse<Void>> deleteAccount(
      Authentication auth,
      @Valid @RequestBody DeleteAccountRequestDto request) {

    String email = extractEmail(auth);
    userService.deleteAccount(email, request.getPassword());

    log.info("계정 삭제 완료: {}", email);
    return ResponseEntity.ok(ApiResponse.success(UserResponse.ACCOUNT_DELETED));
  }

  private String extractEmail(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    return jwt.getClaimAsString("email");
  }

  private String extractKeycloakId(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    return jwt.getClaimAsString("sub");
  }
}