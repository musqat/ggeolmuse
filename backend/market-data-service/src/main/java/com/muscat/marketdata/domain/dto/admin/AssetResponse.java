package com.muscat.marketdata.domain.dto.admin;

import com.muscat.marketdata.domain.entity.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 종목 쓰기 결과와 안내 문구
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetResponse {
    private String symbol;
    private String name;
    private String country;
    private String currency;
    private String assetType;
    private String message;

    public static AssetResponse of(Asset asset, String message) {
        return AssetResponse.builder()
            .symbol(asset.getSymbol())
            .name(asset.getName())
            .country(asset.getCountry())
            .currency(asset.getCurrency())
            .assetType(asset.getAssetType())
            .message(message)
            .build();
    }
}
