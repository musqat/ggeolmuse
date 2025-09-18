package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 백테스팅 전략 실행 및 비교 분석을 통합한 REST API 컨트롤러
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Slf4j
public class BacktestAnalysisController {

  private final BacktestAnalysisService backtestAnalysisService;

  // DCA(적립식) 투자 전략 백테스팅을 실행
  @PostMapping("/strategy/dca")
  public ResponseEntity<StrategyResponse> runDcaStrategy(
      @Valid @RequestBody DcaStrategyRequest request) {

    log.info("DCA 전략 백테스팅 요청: {} - 월{}원",
        request.getSymbol(), request.getMonthlyAmount());

    StrategyResponse result = backtestAnalysisService.runDcaStrategy(request);

    log.info("DCA 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  // 조건부 매수 전략 백테스팅을 실행
  @PostMapping("/strategy/conditional")
  public ResponseEntity<StrategyResponse> runConditionalStrategy(
      @Valid @RequestBody ConditionalStrategyRequest request) {

    log.info("조건부 매수 전략 백테스팅 요청: {} - {}% 하락시 매수",
        request.getSymbol(), request.getDropPercentage());

    StrategyResponse result = backtestAnalysisService.runConditionalStrategy(request);

    log.info("조건부 매수 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }


  // 여러 종목의 투자 성과를 비교 분석
  @PostMapping("/compare/symbols")
  public ResponseEntity<ComparisonResponse> compareSymbols(
      @Valid @RequestBody SymbolComparisonRequest request) {

    log.info("종목 비교 분석 요청: {}", request.getSymbols());

    ComparisonResponse result = backtestAnalysisService.compareSymbols(request);

    log.info("종목 비교 분석 완료: {}개 종목, 최고 수익률 {}%",
        result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  // 다양한 투자 전략의 성과를 비교 분석
  @PostMapping("/compare/strategies")
  public ResponseEntity<ComparisonResponse> compareStrategies(
      @Valid @RequestBody StrategyComparisonRequest request) {

    log.info("전략 비교 분석 요청: {} - {}개 전략",
        request.getSymbol(),
        request.getStrategies() != null ? request.getStrategies().size() : 0);

    ComparisonResponse result = backtestAnalysisService.compareStrategies(request);

    log.info("전략 비교 분석 완료: {}개 전략, 최고 수익률 {}%",
        result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  // 서로 다른 매수 시점의 성과를 비교 분석
  @PostMapping("/compare/timing")
  public ResponseEntity<ComparisonResponse> compareTiming(
      @Valid @RequestBody TimingComparisonRequest request) {

    log.info("타이밍 비교 분석 요청: {} - {}개 시점",
        request.getSymbol(),
        request.getPurchaseDates() != null ? request.getPurchaseDates().size() : 0);

    ComparisonResponse result = backtestAnalysisService.compareTiming(request);

    log.info("타이밍 비교 분석 완룄: {}개 시점, 최고 수익률 {}%",
        result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }


  // 서비스 상태를 확인
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("OK");
  }
}