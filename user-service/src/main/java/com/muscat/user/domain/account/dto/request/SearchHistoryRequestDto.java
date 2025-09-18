package com.muscat.user.domain.account.dto.request;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryRequestDto {

  private String currency;
  private LocalDateTime startDate;
  private LocalDateTime endDate;
  private Integer size;
}