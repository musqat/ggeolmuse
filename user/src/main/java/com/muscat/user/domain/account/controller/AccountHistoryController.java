package com.muscat.user.domain.account.controller;

import com.muscat.user.common.exceptions.BusinessException;
import com.muscat.user.common.responses.AccountHistoryResponse;
import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.domain.account.dto.request.SearchHistoryRequestDto;
import com.muscat.user.domain.account.dto.response.HistoryListResponseDto;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import com.muscat.user.domain.account.service.AccountHistoryService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}/histories")
@RequiredArgsConstructor
@Slf4j
public class AccountHistoryController {

  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int DEFAULT_RECENT_LIMIT = 10;

  private final AccountHistoryService accountHistoryService;
  private final AuthUtil authUtil;

  // 계좌 거래 내역 목록 조회 (페이징)
  @GetMapping
  public ResponseEntity<ApiResponse<HistoryListResponseDto>> getAccountHistories(
      @PathVariable Long accountId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);

    if (size > MAX_PAGE_SIZE) {
      size = MAX_PAGE_SIZE;
    }
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }

    if (from != null && to != null && !to.isAfter(from)) {
      throw new BusinessException(UserResponse.INVALID_INPUT, "파라미터 오류: to는 from보다 뒤여야 합니다.");
    }

    HistoryListResponseDto response = accountHistoryService.getAccountHistories(
        accountId, page, size, userId, from, to
    );

    log.info("이력 조회: userId={}, accountId={}, page={}, size={}, from={}, to={}",
        userId, accountId, page, size, from, to);

    return ResponseEntity.ok(
        ApiResponse.success(AccountHistoryResponse.HISTORY_LIST_FOUND, response));
  }

  // 거래 내역 검색 (통화/기간/최근 N건)
  @PostMapping("/search")
  public ResponseEntity<ApiResponse<List<HistoryResponseDto>>> searchHistories(
      @PathVariable Long accountId,
      @Valid @RequestBody SearchHistoryRequestDto request,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    List<HistoryResponseDto> response;

    if (request.getCurrency() != null) {
      response = accountHistoryService.getHistoriesByCurrency(accountId, request.getCurrency(),
          userId);

    } else if (request.getStartDate() != null && request.getEndDate() != null) {
      if (!request.getEndDate().isAfter(request.getStartDate())) {
        throw new BusinessException(UserResponse.INVALID_INPUT,
            "파라미터 오류: endDate는 startDate보다 뒤여야 합니다.");
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

    return ResponseEntity.ok(
        ApiResponse.success(AccountHistoryResponse.HISTORY_LIST_FOUND, response));
  }

  // 특정 거래 내역 상세 조회
  @GetMapping("/{historyId}")
  public ResponseEntity<ApiResponse<HistoryResponseDto>> getAccountHistory(
      @PathVariable Long accountId,
      @PathVariable Long historyId,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    HistoryResponseDto response = accountHistoryService.getAccountHistory(
        accountId, historyId, userId);

    return ResponseEntity.ok(
        ApiResponse.success(AccountHistoryResponse.HISTORY_FOUND, response));
  }

  // 환전 내역만 조회
  @GetMapping("/exchanges")
  public ResponseEntity<ApiResponse<List<HistoryResponseDto>>> getExchangeHistories(
      @PathVariable Long accountId,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    List<HistoryResponseDto> response = accountHistoryService.getExchangeHistories(accountId,
        userId);

    return ResponseEntity.ok(
        ApiResponse.success(AccountHistoryResponse.HISTORY_LIST_FOUND, response));
  }

  // 특정 통화의 거래 내역 조회
  @GetMapping("/currency/{currency}")
  public ResponseEntity<ApiResponse<List<HistoryResponseDto>>> getHistoriesByCurrency(
      @PathVariable Long accountId,
      @PathVariable String currency,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    List<HistoryResponseDto> response = accountHistoryService.getHistoriesByCurrency(
        accountId, currency, userId);

    return ResponseEntity.ok(
        ApiResponse.success(AccountHistoryResponse.HISTORY_LIST_FOUND, response));
  }

  // 최근 N개 거래 내역 조회
  @GetMapping("/recent")
  public ResponseEntity<ApiResponse<List<HistoryResponseDto>>> getRecentHistories(
      @PathVariable Long accountId,
      @RequestParam(defaultValue = "10") int limit,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    if (limit > MAX_PAGE_SIZE) {
      limit = MAX_PAGE_SIZE;
    }
    if (limit <= 0) {
      limit = DEFAULT_RECENT_LIMIT;
    }

    List<HistoryResponseDto> response = accountHistoryService.getRecentHistories(
        accountId, limit, userId);

    return ResponseEntity.ok(
        ApiResponse.success(AccountHistoryResponse.HISTORY_LIST_FOUND, response));
  }
}
