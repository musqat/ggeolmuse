package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trade/internal")
@RequiredArgsConstructor
@Slf4j
public class TradeApiController {

  private final TradingService tradingService;

  // 종목별 거래 내역 조회
  @GetMapping("/history/{symbol}")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryBySymbol(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String symbol) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getTradesBySymbol(userId, symbol);

    return ResponseEntity.ok(trades);
  }

  // 거래 내역 조회 (페이지네이션)
  @GetMapping("/history")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistory(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getUserTrades(userId, page, size);

    return ResponseEntity.ok(trades);
  }

  // 기간별 거래 내역 조회
  @GetMapping("/history/period")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryByPeriod(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getTradesByDateRange(userId, startDate, endDate);

    return ResponseEntity.ok(trades);
  }

}