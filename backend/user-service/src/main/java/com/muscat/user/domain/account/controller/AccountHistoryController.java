package com.muscat.user.domain.account.controller;

import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.domain.account.dto.request.SearchHistoryRequestDto;
import com.muscat.user.domain.account.dto.response.HistoryListResponseDto;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import com.muscat.user.domain.account.service.AccountHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account History", description = "계좌 거래 내역 관리 API")
@RestController
@RequestMapping("/api/internal/accounts/{accountId}/histories")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class AccountHistoryController {

  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int DEFAULT_RECENT_LIMIT = 10;

  private final AccountHistoryService accountHistoryService;
  private final AuthUtil authUtil;

  @Operation(summary = "계좌 거래 내역 목록 조회", description = "페이징을 지원하는 계좌 거래 내역 목록을 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "거래 내역 조회 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping
  public ResponseEntity<HistoryListResponseDto> getAccountHistories(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기 (최대 100)", example = "20") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "시작 시간 (선택사항)", example = "2024-01-01T00:00:00Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @Parameter(description = "종료 시간 (선택사항)", example = "2024-12-31T23:59:59Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);

    if (size > MAX_PAGE_SIZE) {
      size = MAX_PAGE_SIZE;
    }
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }

    if (from != null && to != null && !to.isAfter(from)) {
      throw new UserException(UserResponse.INVALID_INPUT);
    }

    LocalDateTime fromLocal = from != null ? LocalDateTime.ofInstant(from, java.time.ZoneOffset.UTC) : null;
    LocalDateTime toLocal = to != null ? LocalDateTime.ofInstant(to, java.time.ZoneOffset.UTC) : null;

    HistoryListResponseDto response = accountHistoryService.getAccountHistories(
        accountId, page, size, userId, fromLocal, toLocal
    );

    log.info("이력 조회: userId={}, accountId={}, page={}, size={}, from={}, to={}",
        userId, accountId, page, size, from, to);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "거래 내역 검색", description = "통화, 기간, 최근 N건 등의 조건으로 거래 내역을 검색합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "거래 내역 검색 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 검색 조건"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @PostMapping("/search")
  public ResponseEntity<List<HistoryResponseDto>> searchHistories(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Valid @RequestBody SearchHistoryRequestDto request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    List<HistoryResponseDto> response;

    if (request.getCurrency() != null) {
      response = accountHistoryService.getHistoriesByCurrency(accountId, request.getCurrency(),
          userId);

    } else if (request.getStartDate() != null && request.getEndDate() != null) {
      if (!request.getEndDate().isAfter(request.getStartDate())) {
        throw new UserException(UserResponse.INVALID_INPUT);
      }
      response = accountHistoryService.getHistoriesByDateRange(
          accountId, request.getStartDate(), request.getEndDate(), userId);

    } else {
      int limit = request.getSize() != null ? request.getSize() : DEFAULT_PAGE_SIZE;
      if (limit > MAX_PAGE_SIZE) {
        limit = MAX_PAGE_SIZE;
      }
      if (limit <= 0) {
        limit = DEFAULT_PAGE_SIZE;
      }
      response = accountHistoryService.getRecentHistories(accountId, limit, userId);
    }

    log.info("거래 내역 검색: userId={}, accountId={}, req={}", userId, accountId, request);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "거래 내역 상세 조회", description = "특정 거래 내역의 상세 정보를 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "거래 내역 상세 조회 성공"),
      @ApiResponse(responseCode = "404", description = "계좌 또는 거래 내역을 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping("/{historyId}")
  public ResponseEntity<HistoryResponseDto> getAccountHistory(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Parameter(description = "거래 내역 ID") @PathVariable Long historyId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    HistoryResponseDto response = accountHistoryService.getAccountHistory(
        accountId, historyId, userId);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "환전 내역 조회", description = "계좌의 모든 환전 거래 내역만 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "환전 내역 조회 성공"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping("/exchanges")
  public ResponseEntity<List<HistoryResponseDto>> getExchangeHistories(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    List<HistoryResponseDto> response = accountHistoryService.getExchangeHistories(accountId,
        userId);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "통화별 거래 내역 조회", description = "지정된 통화와 관련된 모든 거래 내역을 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "통화별 거래 내역 조회 성공"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping("/currency/{currency}")
  public ResponseEntity<List<HistoryResponseDto>> getHistoriesByCurrency(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Parameter(description = "통화 코드 (KRW, USD)", example = "KRW") @PathVariable String currency,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    List<HistoryResponseDto> response = accountHistoryService.getHistoriesByCurrency(
        accountId, currency, userId);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "최근 거래 내역 조회", description = "계좌의 최근 N개 거래 내역을 시간 순으로 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "최근 거래 내역 조회 성공"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping("/recent")
  public ResponseEntity<List<HistoryResponseDto>> getRecentHistories(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Parameter(description = "조회할 거래 내역 수 (최대 100)", example = "10") @RequestParam(defaultValue = "10") int limit,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    if (limit > MAX_PAGE_SIZE) {
      limit = MAX_PAGE_SIZE;
    }
    if (limit <= 0) {
      limit = DEFAULT_RECENT_LIMIT;
    }

    List<HistoryResponseDto> response = accountHistoryService.getRecentHistories(
        accountId, limit, userId);

    return ResponseEntity.ok(response);
  }
}
