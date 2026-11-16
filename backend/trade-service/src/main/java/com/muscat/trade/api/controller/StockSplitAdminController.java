package com.muscat.trade.api.controller;

import com.muscat.trade.domain.service.StockSplitBackfillService;
import com.muscat.trade.domain.service.StockSplitBackfillService.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자용 지난 분할 백필
@Tag(name = "Stock Split Admin", description = "지난 주식 분할 반영 (관리자)")
@RestController
@RequestMapping("/api/admin/trade/splits")
@RequiredArgsConstructor
public class StockSplitAdminController {

  private final StockSplitBackfillService stockSplitBackfillService;

  @Operation(summary = "지난 분할 백필 시작",
    description = "체결이 있는 종목마다 첫 거래일 뒤에 난 분할을 다시 판정해 모든 계좌에 반영하는 작업을 백그라운드로 시작"
      + "(결과는 같은 경로 GET)")
  @PostMapping("/backfill")
  public ResponseEntity<Status> backfill() {
    return ResponseEntity.accepted().body(stockSplitBackfillService.start());
  }

  @Operation(summary = "지난 분할 백필 상태",
    description = "진행 중 여부와 마지막 결과(맞춘 체결 수 · 실패한 종목 수 · 보내지 못한 배당 몫 재계산 요청 수 · 끝난 시각)")
  @GetMapping("/backfill")
  public ResponseEntity<Status> backfillStatus() {
    return ResponseEntity.ok(stockSplitBackfillService.status());
  }
}
