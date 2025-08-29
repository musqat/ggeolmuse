package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.service.HoldingsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
public class PortfolioController {

  private final HoldingsService holdingsService;

  // 포트폴리오 조회 (계좌별 필터링 가능)
  @GetMapping
  public ResponseEntity<List<HoldingResponseDto>> getPortfolio(Authentication auth) {
    String userId = extractUserId(auth);
    List<HoldingResponseDto> holdings = holdingsService.getPortfolio(userId, null);
    return ResponseEntity.ok(holdings);
  }

  // 계좌별 포트폴리오 조회
  @GetMapping("/account/{accountId}")
  public ResponseEntity<List<HoldingResponseDto>> getAccountPortfolio(
      Authentication auth,
      @PathVariable String accountId) {

    String userId = extractUserId(auth);
    List<HoldingResponseDto> holdings = holdingsService.getPortfolio(userId, accountId);
    return ResponseEntity.ok(holdings);
  }

  // 특정 종목 보유 현황 조회
  @GetMapping("/account/{accountId}/symbol/{symbol}")
  public ResponseEntity<HoldingResponseDto> getHoldingBySymbol(
      Authentication auth,
      @PathVariable String accountId,
      @PathVariable String symbol) {

    String userId = extractUserId(auth);

    HoldingResponseDto holding = holdingsService.getHoldingBySymbol(userId, accountId, symbol);

    if (holding == null) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(holding);
  }

  // 포트폴리오 종합 정보 (모든 계산 포함)
  @PostMapping("/summary")
  public ResponseEntity<PortfolioSummary> getPortfolioSummary(
      Authentication auth,
      @RequestBody Map<String, BigDecimal> currentPrices) {

    String userId = extractUserId(auth);
    PortfolioSummary summary = holdingsService.getPortfolioSummary(userId, currentPrices);
    return ResponseEntity.ok(summary);
  }

  // JWT에서 사용자 ID 추출
  private String extractUserId(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    return jwt.getClaimAsString("sub");
  }
}