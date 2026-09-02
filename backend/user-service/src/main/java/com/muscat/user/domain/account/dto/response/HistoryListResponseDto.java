package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "계좌 거래 내역 목록 응답")
public record HistoryListResponseDto(
  @Schema(description = "계좌 고유 식별자", example = "1")
  Long accountId,

  @Schema(description = "계좌번호", example = "1001-2024-0001")
  String accountNumber,

  @Schema(description = "계좌명", example = "메인 계좌")
  String accountName,

  @Schema(description = "거래 내역 목록")
  List<HistoryResponseDto> histories,

  @Schema(description = "전체 페이지 수", example = "5")
  int totalPages,

  @Schema(description = "전체 데이터 개수", example = "100")
  long totalElements,

  @Schema(description = "다음 페이지 존재 여부", example = "true")
  boolean hasNext,

  @Schema(description = "이전 페이지 존재 여부", example = "false")
  boolean hasPrevious,

  @Schema(description = "총 입금액", example = "5000000.00")
  BigDecimal totalDeposit,

  @Schema(description = "총 환전액", example = "1000000.00")
  BigDecimal totalExchange
) {

}
