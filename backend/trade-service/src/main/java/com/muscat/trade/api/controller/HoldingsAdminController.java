package com.muscat.trade.api.controller;

import com.muscat.trade.domain.service.HoldingsBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자용 보유 다시 쌓기
@Tag(name = "Holdings Admin", description = "보유 다시 쌓기 (관리자)")
@RestController
@RequestMapping("/api/admin/trade/holdings")
@RequiredArgsConstructor
public class HoldingsAdminController {

  private final HoldingsBackfillService holdingsBackfillService;

  @Operation(summary = "보유 다시 쌓기",
    description = "완료 체결이 있는 모든 계좌 · 종목의 보유를 거래일 순서로 다시 쌓고 바뀐 보유 행 수를 돌려줍니다.")
  @PostMapping("/rebuild")
  public ResponseEntity<Map<String, Integer>> rebuildAll() {
    return ResponseEntity.ok(Map.of("changed", holdingsBackfillService.rebuildAll()));
  }
}
