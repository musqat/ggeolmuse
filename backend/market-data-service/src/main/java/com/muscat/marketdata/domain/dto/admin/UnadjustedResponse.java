package com.muscat.marketdata.domain.dto.admin;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 분할 종목 탐색 상태와 마지막 결과
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnadjustedResponse {
    private boolean running;
    private LocalDate from;
    private int count;
    private List<String> symbols;
    private String finishedAt;
    private long tookMillis;
    private String error;
}
