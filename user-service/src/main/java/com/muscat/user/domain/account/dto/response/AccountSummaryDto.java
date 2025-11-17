package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "계좌 요약 정보")
public record AccountSummaryDto(
  @Schema(description = "계좌 고유 식별자", example = "1")
  Long accountId,             // 계좌 ID (frontend accountId와 일치)

  @Schema(description = "계좌명", example = "메인 계좌")
  String accountName,         // 계좌명

  @Schema(description = "계좌번호", example = "1001-2024-0001")
  String accountNumber,       // 계좌번호

  @Schema(description = "거래 수수료율 (0~5%)", example = "0.0025")
  BigDecimal commissionRate,  // 수수료율

  @Schema(description = "USD 잔액", example = "10000.00")
  BigDecimal usdBalance,      // USD 잔액

  @Schema(description = "KRW 잔액", example = "13000000.00")
  BigDecimal krwBalance,      // KRW 잔액

  @Schema(description = "계좌 생성일시", example = "2024-09-18T10:30:00")
  LocalDateTime createdAt     // 생성일
) {

}
