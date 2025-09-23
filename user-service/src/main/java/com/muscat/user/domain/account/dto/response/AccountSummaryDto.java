package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Schema(description = "계좌 요약 정보")
@Getter
@Builder
public class AccountSummaryDto {

  @Schema(description = "계좌 고유 식별자", example = "1")
  private Long id;                    // 계좌 ID
  @Schema(description = "계좌명", example = "메인 계좌")
  private String accountName;         // 계좌명
  @Schema(description = "계좌번호", example = "1001-2024-0001")
  private String accountNumber;       // 계좌번호
  @Schema(description = "계좌 생성일시", example = "2024-09-18T10:30:00")
  private LocalDateTime createdAt;    // 생성일
}