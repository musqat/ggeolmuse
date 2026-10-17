package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.response.PayoutDiffResponse;
import com.muscat.trade.domain.service.DividendBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자용 배당 몫 백필 · 미리 보기
@Tag(name = "Dividend Admin", description = "배당 몫 백필 · 미리 보기 (관리자)")
@RestController
@RequestMapping("/api/admin/trade/dividends")
@RequiredArgsConstructor
public class DividendAdminController {

  private final DividendBackfillService dividendBackfillService;

  @Operation(summary = "배당 몫 백필", description = "거래 · 몫이 있는 모든 계좌 · 종목의 재계산을 요청합니다.")
  @PostMapping("/reconcile")
  public ResponseEntity<Map<String, Integer>> reconcileAll() {
    return ResponseEntity.ok(Map.of("requested", dividendBackfillService.requestAll()));
  }

  @Operation(summary = "배당 몫 미리 보기", description = "계좌마다 넣은 배당과 받을 몫이 다른 곳을 보여 줍니다.")
  @GetMapping("/payout-diff")
  public ResponseEntity<List<PayoutDiffResponse>> payoutDiff() {
    return ResponseEntity.ok(dividendBackfillService.diff());
  }
}
