package com.muscat.marketdata.domain.dto.admin;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 캔들 재수집 결과(발행 수 · 없는 종목 · 기간)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {
    private int requested;
    private int published;
    private List<String> notFound;
    private LocalDate from;
    private LocalDate to;
}
