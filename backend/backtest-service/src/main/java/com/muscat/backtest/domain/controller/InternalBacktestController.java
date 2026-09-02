package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.service.InvestmentBacktestService;
import com.muscat.backtest.infra.client.dto.InvestmentBacktestResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "내부 백테스트 (서비스 간 통신)", description = "Trade 서비스 전용 — 캐시된 투자 백테스트 결과 조회")
public class InternalBacktestController {

  private final InvestmentBacktestService investmentBacktestService;

  @Operation(
      summary = "캐시된 투자 백테스트 결과 조회 (내부용)",
      description = "userId로 저장된 투자 백테스트 결과를 조회. Trade 서비스 간 통신 전용, 없으면 204")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "캐시된 결과 존재",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InvestmentBacktestResultDto.class))),
      @ApiResponse(responseCode = "204", description = "캐시된 결과 없음", content = @Content)
  })
  @GetMapping("/investment-result/{userId}")
  public ResponseEntity<InvestmentBacktestResultDto> getCachedInvestmentResult(
      @Parameter(description = "조회 대상 사용자 ID", required = true)
      @PathVariable("userId") String userId) {

    log.debug("캐시된 투자 백테스트 결과 조회 요청: userId={}", userId);

    Optional<InvestmentBacktestResult> cachedEntity = investmentBacktestService.getCachedInvestmentResultEntity(
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
