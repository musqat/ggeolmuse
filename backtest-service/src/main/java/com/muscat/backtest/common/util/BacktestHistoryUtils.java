package com.muscat.backtest.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.entity.BacktestHistory;
import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BacktestHistoryUtils {

    private final BacktestHistoryRepository backtestHistoryRepository;
    private final ObjectMapper objectMapper;

    public BacktestHistoryUtils(BacktestHistoryRepository backtestHistoryRepository, ObjectMapper objectMapper) {
        this.backtestHistoryRepository = backtestHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public void saveBacktestHistory(String userId, BacktestType backtestType, Object requestParams) {
        executeWithFallback(() -> {
            try {
                String paramsJson = objectMapper.writeValueAsString(requestParams);
                BacktestHistory history = BacktestHistory.builder()
                    .userId(userId)
                    .backtestType(backtestType)
                    .requestParams(paramsJson)
                    .build();
                backtestHistoryRepository.save(history);
                log.debug("백테스트 히스토리 저장 완료: userId={}, type={}", userId, backtestType);
                return null;
            } catch (JsonProcessingException e) {
                log.warn("JSON 변환 오류: userId={}, error={}", userId, e.getMessage());
                return null;
            }
        }, "백테스트 히스토리 저장", userId);
    }

    private void executeWithFallback(java.util.function.Supplier<Void> operation, String operationName, String userId) {
        try {
            operation.get();
        } catch (Exception e) {
            log.warn("{} 실패: userId={}, error={}", operationName, userId, e.getMessage());
        }
    }
}