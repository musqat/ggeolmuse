package com.muscat.backtest.domain.controller;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.response.ApiResponse;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.service.TradingSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 시뮬레이션 컨트롤러
@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class TradingSimulationController {

  private final TradingSimulationService tradingSimulationService;

  // 백테스팅 시뮬레이션 실행 API 과거 특정 시점에 투자했을 때의 결과를 시뮬레이션
  @PostMapping("/simulation")
  public ResponseEntity<ApiResponse<SimulationResponse>> runSimulation(
      @Valid @RequestBody SimulationRequest request) {

    try {
      log.info("백테스팅 시뮬레이션 요청: {}", request);

      SimulationResponse result = tradingSimulationService.runSimulation(request);

      log.info("백테스팅 시뮬레이션 완료: {} 수익률 {}%",
          request.getSymbol(), result.getTotalReturnPercent());

      return ResponseEntity.status(200).body(
          ApiResponse.of(BacktestResponseCode.SUCCESS, result));

    } catch (Exception e) {
      log.error("백테스팅 시뮬레이션 실패: {}", e.getMessage(), e);
      throw new BacktestException(BacktestResponseCode.SIMULATION_FAILED, e);
    }
  }

  // 사용자 보유 주식 백테스트 조회
  @GetMapping("/investment")
  public ResponseEntity<ApiResponse<InvestmentResponse>> getInvestmentBacktest(
      @AuthenticationPrincipal Jwt jwt) {
    try {
      String userId = jwt.getSubject();

      log.info("사용자 보유 주식 백테스트 조회: {}", userId);

      InvestmentRequest request = new InvestmentRequest();
      request.setUserId(userId);

      // JWT 토큰 값을 그대로 사용 (이미 Bearer 형식으로 전달됨)
      String token = "Bearer " + jwt.getTokenValue();

      InvestmentResponse result = tradingSimulationService.executeInvestment(request, token);

      log.info("보유 주식 백테스트 완료: 사용자 {} - {}",
          userId, result.getStatus());

      return ResponseEntity.status(200).body(
          ApiResponse.of(BacktestResponseCode.SUCCESS, result));

    } catch (Exception e) {
      log.error("보유 주식 백테스트 실패: {}", e.getMessage(), e);
      throw new BacktestException(BacktestResponseCode.INVESTMENT_FAILED, e);
    }
  }

  // 서비스 상태 확인 API
  @GetMapping("/health")
  public ResponseEntity<ApiResponse<String>> health() {
    return ResponseEntity.status(200).body(
        ApiResponse.of(BacktestResponseCode.SUCCESS, "OK"));
  }
}