package com.muscat.backtest.api.controller;

import com.muscat.backtest.common.util.TokenOwner;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading-simulation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "거래 시뮬레이션", description = "백테스팅 시뮬레이션 API")
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
                        "currentDate": "2024-01-01",
                        "investmentAmount": 1000000,
                        "purchasePrice": 130.00,
                        "shares": 5.92,
                        "currentPrice": 195.00,
                        "currentValue": 1154.40,
                        "currentValueKrw": 1523808,
                        "totalReturnKrw": 523808,
                        "totalReturnPercent": 52.38
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
      @Valid @RequestBody SimulationRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    log.info("백테스팅 시뮬레이션 요청: {}", request);

    request.setUserId(TokenOwner.sub(jwt));
    SimulationResponse result = tradingSimulationService.runSimulation(request);

    log.info("백테스팅 시뮬레이션 완료: {} 수익률 {}%, 보유주식 {}주, 재투자={}, 재투자날짜={}",
        request.getSymbol(), result.getTotalReturnPercent(), result.getShares(),
        request.getReinvestDividends(), result.getDividendReinvestDates());

    return ResponseEntity.ok(result);
  }
}