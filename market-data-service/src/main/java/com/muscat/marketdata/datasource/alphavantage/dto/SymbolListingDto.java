package com.muscat.marketdata.datasource.alphavantage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AlphaVantage LISTING_STATUS API CSV 응답 DTO
 * CSV 컬럼: symbol,name,exchange,assetType,ipoDate,delistingDate,status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolListingDto {
    private String symbol;
    private String name;
    private String exchange;
    private String assetType;
    private String ipoDate;
    private String delistingDate;
    private String status;

    /**
     * CSV 행을 파싱하여 DTO 생성
     * @param csvLine CSV 행
     * @return SymbolListingDto
     */
    public static SymbolListingDto fromCsvLine(String csvLine) {
        String[] fields = csvLine.split(",");
        if (fields.length < 7) {
            return null;
        }

        return SymbolListingDto.builder()
            .symbol(fields[0].trim())
            .name(fields[1].trim())
            .exchange(fields[2].trim())
            .assetType(fields[3].trim())
            .ipoDate(fields[4].trim())
            .delistingDate(fields[5].trim())
            .status(fields[6].trim())
            .build();
    }

    /**
     * 활성 상태인지 확인 (status = "Active")
     */
    public boolean isActive() {
        return "Active".equalsIgnoreCase(status);
    }

    /**
     * 주식(Common Stock)인지 확인
     */
    public boolean isStock() {
        return assetType != null &&
               (assetType.equalsIgnoreCase("Stock") ||
                assetType.equalsIgnoreCase("Common Stock"));
    }

    /**
     * ETF인지 확인
     */
    public boolean isETF() {
        return assetType != null && assetType.equalsIgnoreCase("ETF");
    }

    /**
     * 미국 주요 거래소인지 확인 (NYSE, NASDAQ, NYSE ARCA)
     * NYSE ARCA는 ETF의 주요 상장 거래소
     */
    public boolean isMajorExchange() {
        return exchange != null &&
               (exchange.equalsIgnoreCase("NYSE") ||
                exchange.equalsIgnoreCase("NASDAQ") ||
                exchange.equalsIgnoreCase("NYSE ARCA"));
    }
}
