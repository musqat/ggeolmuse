package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.service.TradingSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading-simulation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "거래 시뮬레이션", description = "백테스팅 시뮬레이션 및 투자 검증 API")
public class TradingSimulationController {

  private final TradingSimulationService tradingSimulationService;

  @Operation(
      summary = "백테스팅 시뮬레이션 실행",
      description = "과거 특정 시점에 투자했을 때의 결과를 시뮬레이션합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "백테스팅 시뮬레이션 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = SimulationResponse.class),
              examples = @ExampleObject(
                  value = """
                      {
                        "symbol": "AAPL",
                        "purchaseDate": "2023-01-01",
                        "saleDate": "2024-01-01",
                        "initialInvestment": 10000.00,
                        "finalValue": 12500.00,
                        "totalReturn": 2500.00,
                        "totalReturnPercent": 25.00
                      }
                      """
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청 데이터",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      )
  })
  @PostMapping("/simulation")
  public ResponseEntity<SimulationResponse> runSimulation(
      @Parameter(description = "백테스팅 시뮬레이션 요청 정보", required = true)
      @Valid @RequestBody SimulationRequest request) {

    log.info("백테스팅 시뮬레이션 요청: {}", request);

    SimulationResponse result = tradingSimulationService.runSimulation(request);

    log.info("백테스팅 시뮬레이션 완료: {} 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "사용자 보유 주식 백테스트 조회",
      description = "사용자가 보유한 주식에 대한 백테스트 결과를 조회합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "보유 주식 백테스트 조회 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InvestmentResponse.class)
          )
      )
  })
  @GetMapping("/investment")
  public ResponseEntity<InvestmentResponse> getInvestmentBacktest(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();

    log.info("사용자 보유 주식 백테스트 조회: {}", userId);

    InvestmentRequest request = new InvestmentRequest();
    request.setUserId(userId);

    // JWT 토큰 값을 그대로 사용 (이미 Bearer 형식으로 전달됨)
    String token = "Bearer " + jwt.getTokenValue();

    InvestmentResponse result = tradingSimulationService.executeInvestment(request, token);

    log.info("보유 주식 백테스트 완료: 사용자 {} - {}",
        userId, result.getStatus());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "투자 시뮬레이션 실행",
      description = "특정 투자 시나리오에 대한 시뮬레이션을 실행합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "투자 시뮬레이션 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InvestmentResponse.class)
          )
      )
  })
  @PostMapping("/simulate")
  public ResponseEntity<InvestmentResponse> simulateInvestment(
      @Valid @RequestBody InvestmentRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    log.info("투자 시뮬레이션 요청: {}", request);

    InvestmentResponse response = tradingSimulationService.executeInvestment(request, jwt.getTokenValue());
    return ResponseEntity.ok(response);
  }
}