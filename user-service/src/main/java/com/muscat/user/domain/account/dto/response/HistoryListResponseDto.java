package com.muscat.user.domain.account.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HistoryListResponseDto {

  // 계좌 식별 정보
  private Long accountId;
  private String accountNumber;
  private String accountName;

  // 거래 내역
  private List<HistoryResponseDto> histories;
  
  // 페이징 정보
  private int totalPages;
  private long totalElements;
  private boolean hasNext;
  private boolean hasPrevious;
  
  // 통계 정보
  private BigDecimal totalDeposit;    // 총 입금액
  private BigDecimal totalExchange;   // 총 환전액
}
