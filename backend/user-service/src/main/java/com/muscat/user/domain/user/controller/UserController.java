package com.muscat.user.domain.user.controller;

import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import com.muscat.user.domain.user.dto.request.DeleteAccountRequestDto;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile", description = "사용자 프로필 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class UserController {

  private final UserService userService;
  private final UserMapper userMapper;

  @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
      @ApiResponse(responseCode = "401", description = "인증 실패"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  @GetMapping("/me")
  public ResponseEntity<UserResponseDto> getMyProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    String email = jwt.getClaimAsString("email");
    User user = userService.getOrCreateProfile(jwt.getSubject(), email, nickname(jwt, email));

    UserResponseDto userDto = userMapper.toResponseDto(user);

    return ResponseEntity.ok(userDto);
  }

  @Operation(summary = "프로필 수정", description = "사용자의 프로필 정보를 수정합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "프로필 수정 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
      @ApiResponse(responseCode = "401", description = "인증 실패"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  @PutMapping("/me")
  public ResponseEntity<UserResponseDto> updateProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateProfileRequestDto request) {

    String keycloakId = jwt.getSubject();
    User updatedUser = userService.updateProfile(keycloakId, request);
    UserResponseDto userDto = userMapper.toResponseDto(updatedUser);

    log.info("프로필 수정 완료: keycloakId={}", keycloakId);
    return ResponseEntity.ok(userDto);
  }

  @Operation(summary = "비밀번호 변경", description = "사용자의 비밀번호를 변경합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
      @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치 또는 잘못된 요청 데이터"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @PutMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ChangePasswordRequestDto request) {

    String keycloakId = jwt.getSubject();

    userService.changePassword(keycloakId, request.getCurrentPassword(), request.getNewPassword());

    log.info("비밀번호 변경 완료: keycloakId={}", keycloakId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "계정 삭제", description = "사용자 계정을 영구적으로 삭제합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "계정 삭제 성공"),
      @ApiResponse(responseCode = "400", description = "비밀번호 불일치"),
      @ApiResponse(responseCode = "401", description = "인증 실패"),
      @ApiResponse(responseCode = "403", description = "비밀번호 없는 사용자가 로그인한 지 오래됨(다시 로그인 필요)")
  })
  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteAccount(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestBody(required = false) DeleteAccountRequestDto request) {

    String keycloakId = jwt.getSubject();
    String password = request != null ? request.getPassword() : null;
    userService.deleteAccount(keycloakId, password, jwt.getClaimAsInstant("auth_time"));

    log.info("계정 삭제 완료: keycloakId={}", keycloakId);
    return ResponseEntity.ok().build();
  }

  // 처음 만들 때 쓰는 닉네임(이름 > given_name > email 앞부분)
  private static String nickname(Jwt jwt, String email) {
    String name = jwt.getClaimAsString("name");
    if (name != null) {
      return name;
    }
    String givenName = jwt.getClaimAsString("given_name");
    if (givenName != null) {
      return givenName;
    }
    return email != null ? email.split("@")[0] : null;
  }
}