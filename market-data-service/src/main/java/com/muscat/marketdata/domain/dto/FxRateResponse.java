package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "환율 정보 응답")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxRateResponse {

    @Schema(description = "환율 적용 날짜", example = "2024-09-18", required = true)
    @NotNull
    private LocalDate date;

    @Schema(description = "기준 통화", example = "USD")
    @Builder.Default
    private String baseCcy = "USD";

    @Schema(description = "대상 통화", example = "KRW")
    @Builder.Default
    private String quoteCcy = "KRW";

    @Schema(description = "환율 (기준통화 1단위 대비 대상통화)", example = "1320.50", required = true)
    @NotNull
    private BigDecimal rate;
}