package com.muscat.marketdata.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Schema(description = "한국수출입은행 환율 데이터")
@Slf4j
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoreaEximRateItem {

    @Schema(description = "통화 단위 코드", example = "USD")
    @JsonProperty("cur_unit")
    private String curUnit;

    @Schema(description = "기준환율 (문자열)", example = "1,320.50")
    @JsonProperty("deal_bas_r")
    private String dealBasR;

    @Schema(description = "통화명", example = "미국 달러")
    @JsonProperty("cur_nm")
    private String curNm;

    @JsonIgnore
    public boolean isUsd() {
        return curUnit != null && curUnit.startsWith("USD");
    }

    @JsonIgnore
    public BigDecimal parseRate() {
        if (dealBasR == null) {
            log.debug("환율 데이터가 null입니다");
            return null;
        }

        try {
            String cleaned = dealBasR.replace(",", "").trim();
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("환율 파싱 실패: dealBasR={}, error={}", dealBasR, e.getMessage());
            return null;
        }
    }
}