package com.muscat.user.domain.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "계좌 거래 내역 검색 조건")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryRequestDto {

  @Schema(description = "검색할 통화 코드", example = "KRW")
  private String currency;
  @Schema(description = "검색 시작 일시", example = "2024-01-01T00:00:00")
  private LocalDateTime startDate;
  @Schema(description = "검색 종료 일시", example = "2024-09-18T23:59:59")
  private LocalDateTime endDate;
  @Schema(description = "조회할 데이터 개수", example = "20")
  private Integer size;
}