package com.muscat.user.domain.account.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRequestDto {

  @NotBlank(message = "기본 통화를 입력해주세요")
  private String fromCurrency;     // 환전할 통화 (KRW, USD)

  @NotBlank(message = "변환 통화를 입력해주세요")
  private String toCurrency;       // 환전받을 통화 (KRW, USD)

  @NotNull(message = "환전할 금액을 입력해주세요")
  @DecimalMin(value = "0.01", message = "환전 금액은 0보다 커야 합니다")
  private BigDecimal originalAmount;  // 환전할 원본 금액

  @NotNull(message = "환율을 입력해주세요")
  @DecimalMin(value = "0.01", message = "환율은 0보다 커야 합니다")
  private BigDecimal exchangeRate;    // 환율 (USD/KRW)
}
