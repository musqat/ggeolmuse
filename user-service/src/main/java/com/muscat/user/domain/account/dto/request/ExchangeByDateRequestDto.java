package com.muscat.user.domain.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "특정 날짜 기준 환전 요청 정보")
@Data
@NoArgsConstructor
public class ExchangeByDateRequestDto {
    
    @Schema(description = "기본 통화 코드", example = "KRW", required = true)
    @NotBlank(message = "기본 통화는 필수입니다")
    private String fromCurrency;
    
    @Schema(description = "변환 통화 코드", example = "USD", required = true)
    @NotBlank(message = "변환 통화는 필수입니다")
    private String toCurrency;
    
    @Schema(description = "환전할 금액", example = "100000.00", required = true)
    @NotNull(message = "환전 금액은 필수입니다")
    @DecimalMin(value = "0.01", message = "환전 금액은 0보다 커야 합니다")
    private BigDecimal originalAmount;
    
    @Schema(description = "환전 기준일", example = "2024-09-18", required = true)
    @NotNull(message = "환전 기준일은 필수입니다")
    private LocalDate exchangeDate;
}