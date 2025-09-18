package com.muscat.user.domain.account.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class AccountSummaryDto {

  private Long id;                    // 계좌 ID
  private String accountName;         // 계좌명
  private String accountNumber;       // 계좌번호
  private LocalDateTime createdAt;    // 생성일
}