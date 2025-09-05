package com.muscat.backtest.domain.controller;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.response.ApiResponse;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.common.enums.StrategyType;
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
    public ResponseEntity<ApiResponse<StrategyResponse>> runDcaStrategy(
            @Valid @RequestBody StrategyRequest request) {
        
        log.info("DCA 전략 백테스팅 요청: {} - 월{}원", 
            request.getSymbol(), request.getMonthlyAmount());
        
        // 전략 타입 강제 설정
        request.setStrategyType(StrategyType.DCA);
        
        try {
            StrategyResponse result = backtestAnalysisService.runStrategy(request);
            
            log.info("DCA 전략 완료: {} - 수익률 {}%", 
                request.getSymbol(), result.getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
                
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("DCA 전략 실행 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.STRATEGY_FAILED, e);
        }
    }
    
    // 조건부 매수 전략 백테스팅을 실행
    @PostMapping("/strategy/conditional")
    public ResponseEntity<ApiResponse<StrategyResponse>> runConditionalStrategy(
            @Valid @RequestBody StrategyRequest request) {
        
        try {
            log.info("조건부 매수 전략 백테스팅 요청: {} - {}% 하락시 매수", 
                request.getSymbol(), request.getDropPercentage());
            
            // 전략 타입 강제 설정
            request.setStrategyType(StrategyType.CONDITIONAL_PURCHASE);
            
            StrategyResponse result = backtestAnalysisService.runStrategy(request);
            
            log.info("조건부 매수 전략 완료: {} - 수익률 {}%", 
                request.getSymbol(), result.getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
            
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("조건부 매수 전략 실행 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.STRATEGY_FAILED, e);
        }
    }
    
    // 일반적인 투자 전략 백테스팅을 실행
    @PostMapping("/strategy/run")
    public ResponseEntity<ApiResponse<StrategyResponse>> runStrategy(
            @Valid @RequestBody StrategyRequest request) {
        
        try {
            log.info("전략 백테스팅 요청: {} - {}", 
                request.getSymbol(), request.getStrategyType());
            
            StrategyResponse result = backtestAnalysisService.runStrategy(request);
            
            log.info("전략 백테스팅 완료: {} - {} - 수익률 {}%", 
                request.getSymbol(), request.getStrategyType(), result.getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
            
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("전략 실행 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.STRATEGY_FAILED, e);
        }
    }
    
    // 여러 종목의 투자 성과를 비교 분석
    @PostMapping("/compare/symbols")
    public ResponseEntity<ApiResponse<ComparisonResponse>> compareSymbols(
            @Valid @RequestBody SymbolComparisonRequest request) {
        
        log.info("종목 비교 분석 요청: {}", request.getSymbols());
        
        try {
            ComparisonResponse result = backtestAnalysisService.compareSymbols(request);
            
            log.info("종목 비교 분석 완료: {}개 종목, 최고 수익률 {}%", 
                result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
                
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("종목 비교 분석 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.ANALYSIS_FAILED, e);
        }
    }
    
    // 다양한 투자 전략의 성과를 비교 분석
    @PostMapping("/compare/strategies")
    public ResponseEntity<ApiResponse<ComparisonResponse>> compareStrategies(
            @Valid @RequestBody StrategyComparisonRequest request) {
        
        try {
            log.info("전략 비교 분석 요청: {} - {}개 전략", 
                request.getSymbol(), 
                request.getStrategies() != null ? request.getStrategies().size() : 0);
            
            ComparisonResponse result = backtestAnalysisService.compareStrategies(request);
            
            log.info("전략 비교 분석 완료: {}개 전략, 최고 수익률 {}%", 
                result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
            
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("전략 비교 분석 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.ANALYSIS_FAILED, e);
        }
    }
    
    // 서로 다른 매수 시점의 성과를 비교 분석
    @PostMapping("/compare/timing")
    public ResponseEntity<ApiResponse<ComparisonResponse>> compareTiming(
            @Valid @RequestBody TimingComparisonRequest request) {
        
        try {
            log.info("타이밍 비교 분석 요청: {} - {}개 시점", 
                request.getSymbol(),
                request.getPurchaseDates() != null ? request.getPurchaseDates().size() : 0);
            
            ComparisonResponse result = backtestAnalysisService.compareTiming(request);
            
            log.info("타이밍 비교 분석 완료: {}개 시점, 최고 수익률 {}%", 
                result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
            
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("타이밍 비교 분석 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.ANALYSIS_FAILED, e);
        }
    }
    
    // 통합된 비교 분석을 실행 (종목/전략/타이밍 비교 통합 엔드포인트)
    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<ComparisonResponse>> runComparison(
            @Valid @RequestBody BaseComparisonRequest request) {
        
        try {
            log.info("비교 분석 요청: {}", request.getComparisonType());
            
            ComparisonResponse result = backtestAnalysisService.runComparison(request);
            
            log.info("비교 분석 완료: {} - {}개 항목, 최고 수익률 {}%", 
                request.getComparisonType(),
                result.getItems().size(), 
                result.getBestPerformer().getTotalReturnPercent());
            
            return ResponseEntity.status(200).body(
                ApiResponse.of(BacktestResponseCode.SUCCESS, result));
            
        } catch (BacktestException e) {
            throw e;
        } catch (Exception e) {
            log.error("비교 분석 실패: {}", e.getMessage(), e);
            throw new BacktestException(BacktestResponseCode.ANALYSIS_FAILED, e);
        }
    }
    
    // 서비스 상태를 확인
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.status(200).body(
            ApiResponse.of(BacktestResponseCode.SUCCESS, "OK"));
    }
}