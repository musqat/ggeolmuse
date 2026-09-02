package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.entity.BacktestHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "백테스트 히스토리 응답")
public record BacktestHistoryDto(
    @Schema(description = "백테스트 ID", example = "1")
    Long id,

    @Schema(description = "사용자 ID (이메일)", example = "user@example.com")
    String userId,

    @Schema(description = "백테스트 타입", example = "STRATEGY_SIMULATION")
    BacktestType backtestType,

    @Schema(description = "요청 파라미터 (JSON 문자열)")
    String requestParams,

    @Schema(description = "환율 설정 모드", example = "auto")
    String fxRateMode,

    @Schema(description = "실행 일시", example = "2025-10-14T10:30:00")
    LocalDateTime createdAt
) {
    public static BacktestHistoryDto from(BacktestHistory history) {
        return new BacktestHistoryDto(
            history.getId(),
            history.getUserId(),
            history.getBacktestType(),
            history.getRequestParams(),
            history.getFxRateMode(),
            history.getCreatedAt()
        );
    }
}
