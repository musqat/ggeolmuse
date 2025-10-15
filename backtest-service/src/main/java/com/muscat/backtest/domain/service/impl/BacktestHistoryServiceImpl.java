package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.domain.dto.response.BacktestHistoryDto;
import com.muscat.backtest.domain.entity.BacktestHistory;
import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import com.muscat.backtest.domain.service.BacktestHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BacktestHistoryServiceImpl implements BacktestHistoryService {

    private final BacktestHistoryRepository backtestHistoryRepository;

    @Override
    public Page<BacktestHistoryDto> getUserBacktestHistory(String userId, Pageable pageable) {
        log.debug("사용자 백테스트 히스토리 조회: userId={}", userId);

        Page<BacktestHistory> historyPage = backtestHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return historyPage.map(this::convertToDto);
    }

    private BacktestHistoryDto convertToDto(BacktestHistory history) {
        return BacktestHistoryDto.builder()
            .backtestId(history.getBacktestId())
            .userId(history.getUserId())
            .backtestType(history.getBacktestType())
            .requestParams(history.getRequestParams())
            .fxRateMode(history.getFxRateMode())
            .createdAt(history.getCreatedAt())
            .build();
    }
}
