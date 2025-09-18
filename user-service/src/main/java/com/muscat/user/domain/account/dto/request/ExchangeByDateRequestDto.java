package com.muscat.user.domain.account.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class ExchangeByDateRequestDto {
    
    @NotBlank(message = "기본 통화는 필수입니다")
    private String fromCurrency;
    
    @NotBlank(message = "변환 통화는 필수입니다")
    private String toCurrency;
    
    @NotNull(message = "환전 금액은 필수입니다")
    @DecimalMin(value = "0.01", message = "환전 금액은 0보다 커야 합니다")
    private BigDecimal originalAmount;
    
    @NotNull(message = "환전 기준일은 필수입니다")
    private LocalDate exchangeDate;
}