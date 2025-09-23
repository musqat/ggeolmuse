package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Builder;

@Schema(description = "데이터 수집 배치 처리 결과")
@Builder
public record BatchResult(
    @Schema(description = "총 심볼 개수", example = "100")
    int totalSymbols,
    @Schema(description = "성공한 심볼 개수", example = "95")
    int successCount,
    @Schema(description = "실패한 심볼 개수", example = "5")
    int failureCount,
    @Schema(description = "저장된 총 레코드 수", example = "2850")
    int totalSavedRecords,
    @Schema(description = "데이터 수집 시작일", example = "2024-01-01")
    LocalDate fromDate,
    @Schema(description = "데이터 수집 종료일", example = "2024-09-18")
    LocalDate toDate
) {

  @Override
  public String toString() {
    return String.format(
        "총 %d개 심볼 중 성공 %d개, 실패 %d개, 저장 레코드 %d개 (기간: %s ~ %s)",
        totalSymbols, successCount, failureCount, totalSavedRecords, fromDate, toDate
    );
  }
}
