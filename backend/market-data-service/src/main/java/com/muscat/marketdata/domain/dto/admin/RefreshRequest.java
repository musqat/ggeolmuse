package com.muscat.marketdata.domain.dto.admin;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 지정 종목 캔들 재수집 요청
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {
    private List<String> symbols;
    private LocalDate from;
}
