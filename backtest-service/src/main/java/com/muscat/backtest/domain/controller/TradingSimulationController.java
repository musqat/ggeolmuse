package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.infra.client.dto.InvestmentBacktestResultDto;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trading-simulation")
@RequiredArgsConstructor
@Slf4j
public class TradingSimulationController {

  private final TradingSimulationService tradingSimulationService;

  // 백테스팅 시뮬레이션 실행 API 과거 특정 시점에 투자했을 때의 결과를 시뮬레이션
  @PostMapping("/simulation")
  public ResponseEntity<SimulationResponse> runSimulation(
      @Valid @RequestBody SimulationRequest request) {

    log.info("백테스팅 시뮬레이션 요청: {}", request);

    SimulationResponse result = tradingSimulationService.runSimulation(request);

    log.info("백테스팅 시뮬레이션 완료: {} 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  // 사용자 보유 주식 백테스트 조회
  @GetMapping("/investment")
  public ResponseEntity<InvestmentResponse> getInvestmentBacktest(
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

  // 캐시된 투자 백테스트 결과 조회 API (Trade 서비스용)
  @GetMapping("/api/backtest/investment-result/{userId}")
  public ResponseEntity<InvestmentBacktestResultDto> getCachedInvestmentResult(
      @PathVariable("userId") String userId) {

    log.debug("캐시된 투자 백테스트 결과 조회 요청: userId={}", userId);

    Optional<InvestmentBacktestResult> cachedEntity = tradingSimulationService.getCachedInvestmentResultEntity(
        userId);

    if (cachedEntity.isPresent()) {
      InvestmentBacktestResult entity = cachedEntity.get();

      // Entity를 DTO로 변환
      InvestmentBacktestResultDto dto = InvestmentBacktestResultDto.builder()
          .userId(entity.getUserId())
          .backtestResult(entity.getResultData())
          .calculatedAt(entity.getCalculatedAt())
          .build();

      log.debug("캐시된 결과 반환: userId={}, calculatedAt={}", userId, entity.getCalculatedAt());
      return ResponseEntity.ok(dto);
    } else {
      log.debug("캐시된 결과 없음: userId={}", userId);
      return ResponseEntity.noContent().build();
    }
  }

  // 서비스 상태 확인 API
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("OK");
  }
}