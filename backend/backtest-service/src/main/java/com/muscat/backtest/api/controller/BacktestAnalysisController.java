package com.muscat.backtest.api.controller;

import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.metrics.BacktestMetrics;
import com.muscat.backtest.common.util.TokenOwner;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
  private final BacktestMetrics backtestMetrics;

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
                        "strategyType": "DCA",
                        "totalTransactions": 12,
                        "totalInvested": 1200000,
                        "totalShares": 6.523481,
                        "currentValueKrw": 1565040,
                        "totalReturnKrw": 365040,
                        "totalReturnPercent": 30.42
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
      @Valid @RequestBody DcaStrategyRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("DCA 전략 백테스팅 요청: {} - 회당 {}",
        request.getSymbol(), request.perPurchase());

    StrategyResponse result = backtestMetrics.record("dca", () -> backtestAnalysisService.runDcaStrategy(request));

    log.info("DCA 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "조건부 매수 전략 백테스팅",
      description = "전 거래일 종가보다 장중에 정한 비율만큼 떨어지거나 오른 날 매수하는 전략의 백테스팅을 실행합니다"
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
      @Valid @RequestBody ConditionalStrategyRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("조건부 매수 전략 백테스팅 요청: {} - 전 거래일 종가 대비 {}% {} 시 매수",
        request.getSymbol(), request.getDropPercentage().movePointRight(2),
        PriceDirection.orDrop(request.getDirection()).label());

    StrategyResponse result = backtestMetrics.record("conditional", () -> backtestAnalysisService.runConditionalStrategy(request));

    log.info("조건부 매수 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "이동평균 교차 전략 백테스팅",
      description = "짧은 이동평균이 긴 이동평균을 아래에서 위로 넘는 날 가진 돈 전부로 사고, 위에서 아래로 넘는 날 전량 파는 전략의 백테스팅을 실행합니다. 신호 종목을 주면 그 종목의 이동평균으로 사고팔 날을 정합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "이동평균 교차 전략 백테스팅 성공",
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
  @PostMapping("/strategy/ma-cross")
  public ResponseEntity<StrategyResponse> runMaCrossStrategy(
      @Parameter(description = "이동평균 교차 전략 백테스팅 요청 정보", required = true)
      @Valid @RequestBody MaCrossStrategyRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("이동평균 교차 전략 백테스팅 요청: {} - {}일 · {}일 선",
        request.getSymbol(), request.shortWindowOrDefault(), request.longWindowOrDefault());

    StrategyResponse result = backtestMetrics.record("ma_cross", () -> backtestAnalysisService.runMaCrossStrategy(request));

    log.info("이동평균 교차 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "이동평균 밴드 전략 백테스팅",
      description = "수정 종가가 N 거래일 평균 × (1 + 위 비율) 보다 높은 날 가진 돈 전부로 사고, 평균 × (1 - 아래 비율) 보다 낮은 날 전량 파는 전략의 백테스팅을 실행합니다. 신호 종목을 주면 그 종목의 밴드로 사고팔 날을 정합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "이동평균 밴드 전략 백테스팅 성공",
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
  @PostMapping("/strategy/ma-band")
  public ResponseEntity<StrategyResponse> runMaBandStrategy(
      @Parameter(description = "이동평균 밴드 전략 백테스팅 요청 정보", required = true)
      @Valid @RequestBody MaBandStrategyRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("이동평균 밴드 전략 백테스팅 요청: {} - {}일 평균 +{} · -{}",
        request.getSymbol(), request.bandWindowOrDefault(), request.upperRateOrDefault(), request.lowerRateOrDefault());

    StrategyResponse result = backtestMetrics.record("ma_band", () -> backtestAnalysisService.runMaBandStrategy(request));

    log.info("이동평균 밴드 전략 완료: {} - 수익률 {}%",
        request.getSymbol(), result.getTotalReturnPercent());

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "포트폴리오 백테스팅",
      description = "여러 종목을 줄마다 금액이나 총액 × 비율로 한 번에 또는 적립식으로 사고, 현금 하나로 날마다 합쳐 평가합니다. 리밸런싱 주기를 주면 주기마다 처음 비중으로 팔고 삽니다. 결과에 종목별 넣은 돈 · 지금 가치 · 기여 · 비중과 매매 내역이 있습니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "포트폴리오 백테스팅 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = PortfolioResponse.class)
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청 데이터 (종목 줄 INVALID_PORTFOLIO, 금액 · 비율 INVALID_PORTFOLIO_AMOUNT 등)",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "줄 하나라도 기간 안 시세가 없음",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      )
  })
  @PostMapping("/portfolio")
  public ResponseEntity<PortfolioResponse> runPortfolio(
      @Parameter(description = "포트폴리오 백테스팅 요청 정보", required = true)
      @Valid @RequestBody PortfolioRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("포트폴리오 백테스팅 요청: {} ~ {}", request.getStartDate(), request.getEndDate());

    PortfolioResponse result = backtestMetrics.record("portfolio", () -> backtestAnalysisService.runPortfolio(request));

    log.info("포트폴리오 완료: 수익률 {}%", result.totalReturnPercent());

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
      @Valid @RequestBody SymbolComparisonRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("종목 비교 분석 요청: {}", request.getSymbols());

    ComparisonResponse result = backtestMetrics.record("compare_symbols", () -> backtestAnalysisService.compareSymbols(request));

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
      @Valid @RequestBody StrategyComparisonRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    request.setUserId(TokenOwner.sub(jwt));

    log.info("전략 비교 분석 요청: {} - {}개 전략",
        request.getSymbol(),
        request.getStrategies() != null ? request.getStrategies().size() : 0);

    ComparisonResponse result = backtestMetrics.record("compare_strategies", () -> backtestAnalysisService.compareStrategies(request));

    log.info("전략 비교 분석 완료: {}개 전략, 최고 수익률 {}%",
        result.getItems().size(),
        result.getBestPerformer() != null ? result.getBestPerformer().getTotalReturnPercent() : "-");

    return ResponseEntity.ok(result);
  }

}