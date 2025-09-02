package com.muscat.marketdata.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscat.marketdata.domain.entity.FxRate;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoreaEximRateItem {

    @JsonProperty("cur_unit")
    private String curUnit;

    @JsonProperty("deal_bas_r")
    private String dealBasR;

    @JsonProperty("cur_nm")
    private String curNm;

    @JsonIgnore
    public boolean isUsd() {
        return curUnit != null && curUnit.startsWith("USD");
    }

    @JsonIgnore
    public BigDecimal parseRate() {
        if (dealBasR == null) return null;
        
        try {
            String cleaned = dealBasR.replace(",", "").trim();
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnore
    public FxRate toEntity(@NotNull LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (!isUsd()) return null;
        
        BigDecimal rate = parseRate();
        if (rate == null) return null;
        
        return FxRate.builder()
                .date(date)
                .rate(rate)
                .build();
    }
}