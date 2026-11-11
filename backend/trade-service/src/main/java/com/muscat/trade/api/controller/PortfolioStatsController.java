package com.muscat.trade.api.controller;

import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.domain.dto.response.DividendStatsResponse;
import com.muscat.trade.domain.dto.response.FxStatsResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse;
import com.muscat.trade.domain.service.PortfolioStatsService;
import com.muscat.trade.domain.service.ProfitFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.beans.PropertyEditorSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 계좌 통계(수익 흐름 · 주제 탭) 조회
@RestController
@RequestMapping("/api/portfolio/stats")
@RequiredArgsConstructor
@Tag(name = "포트폴리오 통계", description = "계좌 수익 흐름 · 통계 탭 API")
public class PortfolioStatsController {

  private static final String ACCOUNT = "계좌 ID 또는 all(전체 계좌)";
  private static final String PERIOD = "기간(1M 은 30일 · 3M 은 90일 · 1Y 는 365일 전부터, ALL 은 첫 거래일부터)";

  private final ProfitFlowService profitFlowService;
  private final PortfolioStatsService portfolioStatsService;

  // 요청 값 1M · 3M · 1Y · ALL 을 기간으로(그 밖은 400)
  @InitBinder
  void bindPeriod(WebDataBinder binder) {
    binder.registerCustomEditor(FlowPeriod.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) {
        setValue(FlowPeriod.of(text));
      }
    });
  }

  @Operation(summary = "수익 흐름 조회",
    description = "거래일마다 계산한 원화 수익과 기간 안 최고 · 최저 · 현재 수익(남의 계좌는 403)")
  @GetMapping("/flow")
  public ResponseEntity<ProfitFlowResponse> flow(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = ACCOUNT, example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = PERIOD, schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(profitFlowService.flow(jwt.getSubject(), accountId, period));
  }

  @Operation(summary = "수익 자세히 조회",
    description = "기간 안 달마다 수익 · 판 거래 실현 손익 · 기간 끝에 들고 있는 종목 평가 손익(남의 계좌는 403)")
  @GetMapping("/profit")
  public ResponseEntity<ProfitDetailResponse> profit(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = ACCOUNT, example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = PERIOD, schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(portfolioStatsService.profit(jwt.getSubject(), accountId, period));
  }

  @Operation(summary = "종목별 기여 조회",
    description = "기간 동안 종목마다 늘어난 실현 · 평가 · 배당 · 수수료 원화와 비율(남의 계좌는 403)")
  @GetMapping("/symbols")
  public ResponseEntity<SymbolContributionResponse> symbols(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = ACCOUNT, example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = PERIOD, schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(portfolioStatsService.symbols(jwt.getSubject(), accountId, period));
  }

  @Operation(summary = "배당 조회",
    description = "배당락일이 기간 안인 배당의 세전 · 세금 · 세후, 달마다 · 종목마다 합(원화는 배당락일 환율, 남의 계좌는 403)")
  @GetMapping("/dividends")
  public ResponseEntity<DividendStatsResponse> dividends(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = ACCOUNT, example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = PERIOD, schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(portfolioStatsService.dividends(jwt.getSubject(), accountId, period));
  }

  @Operation(summary = "환율 효과 조회",
    description = "환전한 원금의 기간 동안 환율 효과와 기간 안 환전 기록(남의 계좌는 403, 환전 기록을 못 받으면 502)")
  @GetMapping("/fx")
  public ResponseEntity<FxStatsResponse> fx(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = ACCOUNT, example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = PERIOD, schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(portfolioStatsService.fx(jwt.getSubject(), accountId, period));
  }

  @Operation(summary = "거래 패턴 조회",
    description = "기간 안 완료 체결의 매수 · 매도 수 · 거래한 날 · 평균 보유 일수 · 승률 · 요일마다 체결 수 · "
      + "많이 거래한 종목 5개(남의 계좌는 403)")
  @GetMapping("/habits")
  public ResponseEntity<TradingHabitsResponse> habits(
    @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
    @Parameter(description = ACCOUNT, example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = PERIOD, schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(portfolioStatsService.habits(jwt.getSubject(), accountId, period));
  }
}
