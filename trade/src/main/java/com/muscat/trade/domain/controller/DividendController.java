package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.entity.DividendHistory;
import com.muscat.trade.domain.service.DividendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dividend")
@RequiredArgsConstructor
@Slf4j
public class DividendController {

  private final DividendService dividendService;

  // 배당 요약 정보 조회
  @GetMapping("/summary/{year}")
  public ResponseEntity<Map<String, BigDecimal>> getDividendSummary(
      Authentication auth,
      @PathVariable int year) {

    String userId = extractUserId(auth);
    Map<String, BigDecimal> summary = dividendService.getDividendSummary(userId, year);
    return ResponseEntity.ok(summary);
  }

  // 배당 내역 조회
  @GetMapping("/history")
  public ResponseEntity<List<DividendHistory>> getDividendHistory(Authentication auth) {
    String userId = extractUserId(auth);
    List<DividendHistory> history = dividendService.getDividendHistory(userId);
    return ResponseEntity.ok(history);
  }

  // 연도별 배당 내역 조회
  @GetMapping("/history/{year}")
  public ResponseEntity<List<DividendHistory>> getDividendHistoryByYear(
      Authentication auth,
      @PathVariable int year) {
    String userId = extractUserId(auth);
    List<DividendHistory> history = dividendService.getDividendHistoryByYear(userId, year);
    return ResponseEntity.ok(history);
  }

  // 종목별 배당 내역 조회
  @GetMapping("/history/symbol/{symbol}")
  public ResponseEntity<List<DividendHistory>> getDividendHistoryBySymbol(
      Authentication auth,
      @PathVariable String symbol) {
    String userId = extractUserId(auth);
    List<DividendHistory> history = dividendService.getDividendHistoryBySymbol(userId, symbol);
    return ResponseEntity.ok(history);
  }

  // 수동 배당 처리 (관리자용)
  @PostMapping("/process")
  public ResponseEntity<Void> processDividendManually(
      @RequestParam String symbol,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dividendDate,
      @RequestParam BigDecimal dividendPerShare) {

    log.info("수동 배당 처리 요청: symbol={}, date={}, dividendPerShare={}", 
        symbol, dividendDate, dividendPerShare);

    dividendService.processDividendForSymbol(symbol, dividendDate, dividendPerShare);

    return ResponseEntity.ok().build();
  }


  // 일일 배당 처리 수동 실행 (관리자용)
  @PostMapping("/process-daily")
  public ResponseEntity<Void> processDailyDividendsManually() {
    log.info("수동 일일 배당 처리 실행 요청");

    dividendService.processDailyDividends();

    return ResponseEntity.ok().build();
  }

  // JWT에서 사용자 ID 추출
  private String extractUserId(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    return jwt.getClaimAsString("sub");
  }
}