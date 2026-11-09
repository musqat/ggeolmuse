package com.muscat.trade.api.controller;

import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
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

// 계좌 통계(수익 흐름) 조회
@RestController
@RequestMapping("/api/portfolio/stats")
@RequiredArgsConstructor
@Tag(name = "포트폴리오 통계", description = "계좌 수익 흐름 API")
public class PortfolioStatsController {

  private final ProfitFlowService profitFlowService;

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
    @Parameter(description = "계좌 ID 또는 all(전체 계좌)", example = "all")
    @RequestParam("accountId") String accountId,
    @Parameter(description = "기간(1M 은 30일 · 3M 은 90일 · 1Y 는 365일 전부터, ALL 은 첫 거래일부터)",
      schema = @Schema(type = "string", allowableValues = {"1M", "3M", "1Y", "ALL"}))
    @RequestParam("period") FlowPeriod period) {
    return ResponseEntity.ok(profitFlowService.flow(jwt.getSubject(), accountId, period));
  }
}
