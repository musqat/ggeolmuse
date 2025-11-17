package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.infra.client.dto.InvestmentBacktestResultDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스 간 통신용 백테스트 컨트롤러
 * Trade 서비스에서만 호출되는 API들
 */
@RestController
@RequestMapping("/api/internal/backtest")
@RequiredArgsConstructor
@Slf4j
public class InternalBacktestController {

  private final TradingSimulationService tradingSimulationService;

  /**
   * 투자 백테스트 결과 조회
   */
  @GetMapping("/investment-result/{userId}")
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
}
