package com.muscat.marketdata.domain.dto;

import java.time.LocalDate;
import lombok.Builder;

@Builder
public record BatchResult(
    int totalSymbols,
    int successCount,
    int failureCount,
    int totalSavedRecords,
    LocalDate fromDate,
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
