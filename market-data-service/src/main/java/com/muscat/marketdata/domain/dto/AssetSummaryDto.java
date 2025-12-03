package com.muscat.marketdata.domain.dto;

import com.muscat.marketdata.domain.entity.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Asset 요약 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetSummaryDto {

    // 기본 Asset 정보
    private String symbol;
    private String name;
    private String country;
    private String currency;
    private String assetType;
    private Long marketCap;
    private Boolean active;
    private LocalDate delistedDate;

    // 추가 계산 필드
    private BigDecimal currentPrice;    // 최신 Candle의 종가
    private LocalDate latestDataDate;   // 가장 최근 Candle 데이터의 날짜

    public static AssetSummaryDto from(Asset asset) {
        return AssetSummaryDto.builder()
            .symbol(asset.getSymbol())
            .name(asset.getName())
            .country(asset.getCountry())
            .currency(asset.getCurrency())
            .assetType(asset.getAssetType())
            .marketCap(asset.getMarketCap())
            .active(asset.getActive())
            .delistedDate(asset.getDelistedDate())
            .build();
    }

    public static AssetSummaryDto of(Asset asset, BigDecimal currentPrice, LocalDate latestDataDate) {
        return AssetSummaryDto.builder()
            .symbol(asset.getSymbol())
            .name(asset.getName())
            .country(asset.getCountry())
            .currency(asset.getCurrency())
            .assetType(asset.getAssetType())
            .marketCap(asset.getMarketCap())
            .active(asset.getActive())
            .delistedDate(asset.getDelistedDate())
            .currentPrice(currentPrice)
            .latestDataDate(latestDataDate)
            .build();
    }
}
