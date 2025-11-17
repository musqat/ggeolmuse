package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "계좌 정보")
public record AccountResponseDto(
  // 기본 정보
  @Schema(description = "계좌 ID", example = "12345")
  Long id,

  @Schema(description = "계좌명", example = "투자계좌1")
  String accountName,

  @Schema(description = "계좌번호", example = "ACC_20240918_001")
  String accountNumber,

  @Schema(description = "계좌 생성일시", example = "2024-09-18T10:30:00")
  LocalDateTime createdAt,

  // 잔액 정보
  @Schema(description = "KRW 잔액", example = "1000000.00")
  BigDecimal balanceKrw,           // KRW 잔액

  @Schema(description = "USD 잔액", example = "500.00")
  BigDecimal balanceUsd,           // USD 잔액

  // 환율 정보
  @Schema(description = "평균 매입 환율", example = "1350.50")
  BigDecimal avgExchangeRate,      // 평균 매입 환율

  @Schema(description = "총 환전한 KRW 금액", example = "675250.00")
  BigDecimal totalExchangedKrw,    // 총 환전한 KRW 금액

  // 설정 정보
  @Schema(description = "수수료율", example = "0.002")
  BigDecimal commissionRate,       // 수수료율

  @Schema(description = "슬리피지율", example = "0.001")
  BigDecimal slippageRate          // 슬리피지율
) {

}
