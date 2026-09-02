package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.service.HoldingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "포트폴리오 관리", description = "사용자 포트폴리오 조회 및 보유 종목 관리 API")
public class PortfolioController {

  private final HoldingsService holdingsService;

  @Operation(
    summary = "전체 포트폴리오 조회",
    description = "사용자의 모든 계좌에 대한 보유 종목을 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "포트폴리오 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = HoldingResponseDto.class)
      )
    )
  })
  @GetMapping
  public ResponseEntity<List<HoldingResponseDto>> getPortfolio(
    @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
    @AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();
    List<HoldingResponseDto> holdings = holdingsService.getPortfolio(userId, null);
    return ResponseEntity.ok(holdings);
  }

  @Operation(
    summary = "계좌별 포트폴리오 조회",
    description = "특정 계좌의 보유 종목을 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "계좌별 포트폴리오 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = HoldingResponseDto.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "계좌를 찾을 수 없음",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @GetMapping("/account/{accountId}")
  public ResponseEntity<List<HoldingResponseDto>> getAccountPortfolio(
    @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
    @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = "계좌 ID", required = true, example = "1")
    @PathVariable("accountId") String accountId) {

    String userId = jwt.getSubject();
    List<HoldingResponseDto> holdings = holdingsService.getPortfolio(userId,
      Long.valueOf(accountId));
    log.debug("포트폴리오 응답: accountId={}, holdings={}, firstHolding={}",
      accountId, holdings.size(),
      holdings.isEmpty() ? "none" : String.format("symbol=%s,currentPrice=%s",
        holdings.getFirst().symbol(), holdings.getFirst().currentPrice()));
    return ResponseEntity.ok(holdings);
  }

  @Operation(
    summary = "특정 종목 보유 현황 조회",
    description = "특정 계좌에서 특정 종목의 보유 현황을 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "종목 보유 현황 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = HoldingResponseDto.class),
        examples = @ExampleObject(
          value = """
            {
              "symbol": "AAPL",
              "quantity": 25,
              "averagePrice": 230.50,
              "currentPrice": 238.15,
              "totalValue": 5953.75,
              "unrealizedGainLoss": 191.25,
              "unrealizedGainLossPercent": 3.32
            }
            """
        )
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "계좌 또는 종목을 찾을 수 없음",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @GetMapping("/account/{accountId}/symbol/{symbol}")
  public ResponseEntity<HoldingResponseDto> getHoldingBySymbol(
    @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
    @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = "계좌 ID", required = true, example = "1")
    @PathVariable("accountId") String accountId,
    @Parameter(description = "종목 코드", required = true, example = "AAPL")
    @PathVariable("symbol") String symbol) {

    String userId = jwt.getSubject();

    HoldingResponseDto holding = holdingsService.getHoldingBySymbol(userId, Long.valueOf(accountId),
      symbol);

    if (holding == null) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(holding);
  }

  @Operation(
    summary = "포트폴리오 종합 정보 조회",
    description = "사용자의 전체 포트폴리오 종합 정보를 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "포트폴리오 종합 정보 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = PortfolioSummary.class)
      )
    )
  })
  @PostMapping("/summary")
  public ResponseEntity<PortfolioSummary> getPortfolioSummary(
    @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
    @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = "종목별 현재가 정보", required = true)
    @RequestBody Map<String, BigDecimal> currentPrices) {

    String userId = jwt.getSubject();
    PortfolioSummary summary = holdingsService.getPortfolioSummary(userId, currentPrices);
    return ResponseEntity.ok(summary);
  }

  @Operation(
    summary = "백테스트 결과 포함 포트폴리오 종합 정보 조회",
    description = "백테스트 분석 결과를 포함한 포트폴리오 종합 정보를 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "백테스트 포함 포트폴리오 정보 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = PortfolioSummary.class)
      )
    )
  })
  @PostMapping("/summary-with-backtest")
  public ResponseEntity<PortfolioSummary> getPortfolioSummaryWithBacktest(
    @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
    @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = "종목별 현재가 정보", required = true)
    @RequestBody Map<String, BigDecimal> currentPrices,
    @Parameter(description = "백테스트 서비스 호출용 Authorization 헤더", required = true)
    @RequestHeader("Authorization") String authorization) {

    String userId = jwt.getSubject();
    PortfolioSummary summary = holdingsService.getPortfolioSummaryWithBacktest(userId,
      currentPrices, authorization);
    return ResponseEntity.ok(summary);
  }

}
