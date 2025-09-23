package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "백테스트 분석", description = "투자 전략 백테스팅 및 성과 분석 API")
public class BacktestAnalysisController {

  private final BacktestAnalysisService backtestAnalysisService;

  @Operation(
      summary = "DCA 투자 전략 백테스팅",
      description = "적립식(Dollar Cost Averaging) 투자 전략의 백테스팅을 실행합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "DCA 전략 백테스팅 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = StrategyResponse.class),
              examples = @ExampleObject(
                  value = """
                      {
                        "symbol": "AAPL",
                        "strategyName": "DCA",
                        "totalInvestment": 12000.00,
                        "finalValue": 15650.00,
                        "totalReturn": 3650.00,
                        "totalReturnPercent": 30.42,
                        "transactions": 12
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
  @PostMapping("/strategy/dca")
  public ResponseEntity<StrategyResponse> runDcaStrategy(
      @Parameter(description = "DCA 전략 백테스팅 요청 정보", required = true)
      @Valid @RequestBody DcaStrategyRequest request) {

    log.info("DCA 전략 백테스팅 요청: {} - 월{}원",
        request.getSymbol(), request.getMonthlyAmount());

    StrategyResponse result = backtestAnalysisService.runDcaStrategy(request);

    log.info("DCA 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "조건부 매수 전략 백테스팅",
      description = "특정 하락률 조건에서 매수하는 전략의 백테스팅을 실행합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "조건부 매수 전략 백테스팅 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = StrategyResponse.class)
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
  @PostMapping("/strategy/conditional")
  public ResponseEntity<StrategyResponse> runConditionalStrategy(
      @Parameter(description = "조건부 매수 전략 백테스팅 요청 정보", required = true)
      @Valid @RequestBody ConditionalStrategyRequest request) {

    log.info("조건부 매수 전략 백테스팅 요청: {} - {}% 하락시 매수",
        request.getSymbol(), request.getDropPercentage());

    StrategyResponse result = backtestAnalysisService.runConditionalStrategy(request);

    log.info("조건부 매수 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }


  @Operation(
      summary = "종목별 성과 비교 분석",
      description = "여러 종목의 투자 성과를 비교 분석합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "종목별 성과 비교 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ComparisonResponse.class)
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
  @PostMapping("/compare/symbols")
  public ResponseEntity<ComparisonResponse> compareSymbols(
      @Parameter(description = "종목별 비교 분석 요청 정보", required = true)
      @Valid @RequestBody SymbolComparisonRequest request) {

    log.info("종목 비교 분석 요청: {}", request.getSymbols());

    ComparisonResponse result = backtestAnalysisService.compareSymbols(request);

    log.info("종목 비교 분석 완료: {}개 종목, 최고 수익률 {}%",
        result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "투자 전략별 성과 비교 분석",
      description = "다양한 투자 전략의 성과를 비교 분석합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "전략별 성과 비교 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ComparisonResponse.class)
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
  @PostMapping("/compare/strategies")
  public ResponseEntity<ComparisonResponse> compareStrategies(
      @Parameter(description = "전략별 비교 분석 요청 정보", required = true)
      @Valid @RequestBody StrategyComparisonRequest request) {

    log.info("전략 비교 분석 요청: {} - {}개 전략",
        request.getSymbol(),
        request.getStrategies() != null ? request.getStrategies().size() : 0);

    ComparisonResponse result = backtestAnalysisService.compareStrategies(request);

    log.info("전략 비교 분석 완료: {}개 전략, 최고 수익률 {}%",
        result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "매수 타이밍별 성과 비교 분석",
      description = "서로 다른 매수 시점의 성과를 비교 분석합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "타이밍별 성과 비교 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ComparisonResponse.class)
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
  @PostMapping("/compare/timing")
  public ResponseEntity<ComparisonResponse> compareTiming(
      @Parameter(description = "타이밍별 비교 분석 요청 정보", required = true)
      @Valid @RequestBody TimingComparisonRequest request) {

    log.info("타이밍 비교 분석 요청: {} - {}개 시점",
        request.getSymbol(),
        request.getPurchaseDates() != null ? request.getPurchaseDates().size() : 0);

    ComparisonResponse result = backtestAnalysisService.compareTiming(request);

    log.info("타이밍 비교 분석 완룄: {}개 시점, 최고 수익률 {}%",
        result.getItems().size(), result.getBestPerformer().getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

}