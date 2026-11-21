package com.muscat.user.domain.user.controller;

import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.domain.user.dto.response.AdminRoleResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal User", description = "서비스 사이 사용자 조회 API(서비스 토큰)")
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InternalUserController {

  private final AuthUtil authUtil;

  @Operation(summary = "관리자 여부 조회", description = "Keycloak ID 사용자가 활성 · 이메일 인증 · DB 역할 관리자인지 조회합니다")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "조회 성공(없는 사용자 · 비활성 · 인증 전 · 일반 사용자는 false)"),
    @ApiResponse(responseCode = "400", description = "Keycloak ID 없음"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "403", description = "서비스 토큰이 아님")
  })
  @GetMapping("/admin-role")
  public ResponseEntity<AdminRoleResponseDto> getAdminRole(
    @Parameter(description = "사용자 Keycloak ID(사용자 토큰의 sub)") @RequestParam String keycloakId) {

    return ResponseEntity.ok(new AdminRoleResponseDto(authUtil.isActiveAdmin(keycloakId)));
  }
}
