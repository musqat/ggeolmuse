package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "계좌 거래 내역 목록 응답")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HistoryListResponseDto {

  // 계좌 식별 정보
  @Schema(description = "계좌 고유 식별자", example = "1")
  private Long accountId;
  @Schema(description = "계좌번호", example = "1001-2024-0001")
  private String accountNumber;
  @Schema(description = "계좌명", example = "메인 계좌")
  private String accountName;

  // 거래 내역
  @Schema(description = "거래 내역 목록")
  private List<HistoryResponseDto> histories;
  
  // 페이징 정보
  @Schema(description = "전체 페이지 수", example = "5")
  private int totalPages;
  @Schema(description = "전체 데이터 개수", example = "100")
  private long totalElements;
  @Schema(description = "다음 페이지 존재 여부", example = "true")
  private boolean hasNext;
  @Schema(description = "이전 페이지 존재 여부", example = "false")
  private boolean hasPrevious;
  
  // 통계 정보
  @Schema(description = "총 입금액", example = "5000000.00")
  private BigDecimal totalDeposit;    // 총 입금액
  @Schema(description = "총 환전액", example = "1000000.00")
  private BigDecimal totalExchange;   // 총 환전액
}
