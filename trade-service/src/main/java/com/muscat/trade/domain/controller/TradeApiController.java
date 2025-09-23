package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/trade-history")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "거래 내역 조회", description = "사용자 거래 내역 조회 및 분석 API")
public class TradeApiController {

  private final TradingService tradingService;

  @Operation(
      summary = "종목별 거래 내역 조회",
      description = "특정 종목에 대한 사용자의 모든 거래 내역을 조회합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "종목별 거래 내역 조회 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradeResponseDto.class)
          )
      )
  })
  @GetMapping("/history/{symbol}")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryBySymbol(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "종목 코드", required = true, example = "AAPL")
      @PathVariable String symbol) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getTradesBySymbol(userId, symbol);

    return ResponseEntity.ok(trades);
  }

  @Operation(
      summary = "거래 내역 조회 (페이지네이션)",
      description = "사용자의 모든 거래 내역을 페이지네이션으로 조회합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "거래 내역 조회 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradeResponseDto.class)
          )
      )
  })
  @GetMapping("/history")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistory(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기", example = "20")
      @RequestParam(defaultValue = "20") int size) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getUserTrades(userId, page, size);

    return ResponseEntity.ok(trades);
  }

  @Operation(
      summary = "기간별 거래 내역 조회",
      description = "지정된 기간 동안의 사용자 거래 내역을 조회합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "기간별 거래 내역 조회 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradeResponseDto.class)
          )
      )
  })
  @GetMapping("/history/period")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryByPeriod(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "조회 시작일", required = true, example = "2024-01-01")
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(description = "조회 종료일", required = true, example = "2024-12-31")
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getTradesByDateRange(userId, startDate, endDate);

    return ResponseEntity.ok(trades);
  }

}