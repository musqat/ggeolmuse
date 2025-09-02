package com.muscat.marketdata.domain.dto;

import com.muscat.marketdata.domain.entity.FxRate;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxRateResponse {

    @NotNull
    private LocalDate date;

    @Builder.Default
    private String baseCcy = "USD";

    @Builder.Default
    private String quoteCcy = "KRW";

    @NotNull
    private BigDecimal rate;

    public static FxRateResponse from(FxRate entity) {
        return FxRateResponse.builder()
                .date(entity.getDate())
                .rate(entity.getRate())
                .build();
    }
}