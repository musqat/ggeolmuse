package com.muscat.user.domain.account.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.muscat.user.domain.account.entity.AccountHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "계좌 장부의 환전 기록")
public record ExchangeRecordDto(
  @Schema(description = "계좌 ID", example = "3")
  Long accountId,

  @Schema(description = "환전 시각", example = "2024-09-02T10:15:30")
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  LocalDateTime exchangedAt,

  @Schema(description = "낸 통화", example = "KRW")
  String fromCurrency,

  @Schema(description = "받은 통화", example = "USD")
  String toCurrency,

  @Schema(description = "낸 금액(낸 통화)", example = "1300000.00")
  BigDecimal originalAmount,

  @Schema(description = "받은 금액(받은 통화)", example = "1000.00")
  BigDecimal exchangedAmount,

  @Schema(description = "환율(1 USD 당 KRW)", example = "1300.000000")
  BigDecimal exchangeRate
) {

  public static ExchangeRecordDto from(AccountHistory history) {
    return new ExchangeRecordDto(
      history.getAccount().getId(),
      history.getCreatedAt(),
      history.getFromCurrency(),
      history.getToCurrency(),
      history.getOriginalAmount(),
      history.getAmount(),
      history.getExchangeRate()
    );
  }
}
