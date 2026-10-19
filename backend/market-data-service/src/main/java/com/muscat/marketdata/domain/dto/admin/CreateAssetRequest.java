package com.muscat.marketdata.domain.dto.admin;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 종목 추가 요청. 이름 · 나라 · 통화 · 종류는 비워도 됨
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssetRequest {
    private String symbol;
    private String name;      // optional
    private String country;   // optional
    private String currency;  // optional
    private String assetType; // optional

    private boolean collectData = false;
    private LocalDate fromDate;
    private LocalDate toDate;
    private boolean includeDividends = false;
}
