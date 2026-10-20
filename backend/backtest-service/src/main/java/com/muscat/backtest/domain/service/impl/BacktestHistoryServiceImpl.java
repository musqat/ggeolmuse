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
    @Transactional
    public Page<BacktestHistoryDto> getUserBacktestHistory(String owner, String legacyOwner, Pageable pageable) {
        if (legacyOwner != null && !legacyOwner.equals(owner)) {
            int moved = backtestHistoryRepository.reassignOwner(legacyOwner, owner);
            if (moved > 0) {
                log.info("이메일로 남은 백테스트 이력 {}건을 사용자 식별자로 옮김", moved);
            }
        }
        log.debug("사용자 백테스트 히스토리 조회: owner={}", owner);

        Page<BacktestHistory> historyPage = backtestHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(owner, pageable);

        return historyPage.map(BacktestHistoryDto::from);
    }
}
