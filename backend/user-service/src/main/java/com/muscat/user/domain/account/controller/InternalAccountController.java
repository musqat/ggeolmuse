package com.muscat.user.domain.account.controller;

import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.domain.account.dto.response.ExchangeRecordDto;
import com.muscat.user.domain.account.service.AccountHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal Account", description = "서비스 사이 계좌 조회 API")
@RestController
@RequestMapping("/api/internal/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InternalAccountController {

  private final AccountHistoryService accountHistoryService;
  private final AuthUtil authUtil;

  @Operation(summary = "환전 기록 조회", description = "토큰 사용자의 모든 계좌 환전 기록을 오래된 것부터 조회합니다")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "조회 성공(기록이 없으면 빈 배열)"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "403", description = "비활성화된 계정"),
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  @GetMapping("/exchanges")
  public ResponseEntity<List<ExchangeRecordDto>> getExchangeRecords(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    return ResponseEntity.ok(accountHistoryService.getExchangeRecords(userId));
  }
}
