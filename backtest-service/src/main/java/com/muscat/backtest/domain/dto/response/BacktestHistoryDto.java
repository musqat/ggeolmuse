package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.type.BacktestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "백테스트 히스토리 응답")
public class BacktestHistoryDto {

    @Schema(description = "백테스트 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String backtestId;

    @Schema(description = "사용자 ID (이메일)", example = "user@example.com")
    private String userId;

    @Schema(description = "백테스트 타입", example = "STRATEGY_SIMULATION")
    private BacktestType backtestType;

    @Schema(description = "요청 파라미터 (JSON 문자열)")
    private String requestParams;

    @Schema(description = "환율 설정 모드", example = "auto")
    private String fxRateMode;

    @Schema(description = "실행 일시", example = "2025-10-14T10:30:00")
    private LocalDateTime createdAt;
}
