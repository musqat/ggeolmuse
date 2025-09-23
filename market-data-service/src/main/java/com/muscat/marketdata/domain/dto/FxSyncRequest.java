package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Schema(description = "환율 데이터 동기화 요청")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxSyncRequest {

    @Schema(description = "동기화 시작 날짜", example = "2024-01-01", required = true)
    @NotNull
    private LocalDate startDate;

    @Schema(description = "동기화 종료 날짜", example = "2024-09-18", required = true)
    @NotNull
    private LocalDate endDate;

}