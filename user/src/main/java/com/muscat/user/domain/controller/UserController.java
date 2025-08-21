package com.muscat.user.domain.controller;

import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.dto.ChangePasswordRequest;
import com.muscat.user.domain.dto.DeleteAccountRequest;
import com.muscat.user.domain.dto.UpdateProfileRequest;
import com.muscat.user.domain.dto.UserResponseDto;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.repository.UserRepository;
import com.muscat.user.domain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private final UserRepository userRepository;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponseDto>> getMyProfile(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    UserResponseDto userDto = UserResponseDto.from(user);
    log.info("프로필 조회 완료: {}", email);

    return ResponseEntity.ok(ApiResponse.success(UserResponse.LOGIN_SUCCESS, userDto));
  }

  @PutMapping("/me")
  public ResponseEntity<ApiResponse<UserResponseDto>> updateProfile(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    User updatedUser = userService.updateProfile(email, request);
    UserResponseDto userDto = UserResponseDto.from(updatedUser);

    log.info("프로필 수정 완료: {}", email);
    return ResponseEntity.ok(ApiResponse.success(UserResponse.PROFILE_UPDATED, userDto));
  }

  @PutMapping("/me/password")
  public ResponseEntity<ApiResponse<Void>> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    userService.changePassword(email, request);

    log.info("비밀번호 변경 완료: {}", email);
    return ResponseEntity.ok(ApiResponse.success(UserResponse.PASSWORD_CHANGED));
  }

  @DeleteMapping("/me")
  public ResponseEntity<ApiResponse<Void>> deleteAccount(Authentication auth, @Valid @RequestBody DeleteAccountRequest request) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    userService.deleteAccount(email, request.getPassword());

    log.info("계정 삭제 완료: {}", email);
    return ResponseEntity.ok(ApiResponse.success(UserResponse.ACCOUNT_DELETED));
  }
}