package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import java.time.LocalDate;
import java.util.List;

// 배당 이력 조회
public interface DividendService {

    // 기간이 비면 그쪽 경계 없이 찾음
    List<DividendDto> getDividendHistory(String symbol, LocalDate startDate, LocalDate endDate);
}
