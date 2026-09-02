package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "매수 시점 비교 분석 요청")
@Data
@EqualsAndHashCode(callSuper = true)
public class TimingComparisonRequest extends BaseComparisonRequest {
    
    @Schema(description = "비교할 종목 코드", example = "AAPL", required = true)
    @NotBlank(message = "종목 코드는 필수입니다")
    private String symbol;
    
    @Schema(description = "비교할 매수 시점 목록", example = "[\"2024-01-15\", \"2024-03-15\", \"2024-06-15\"]", required = true)
    @NotEmpty(message = "비교할 매수 시점은 최소 2개 이상이어야 합니다")
    private List<LocalDate> purchaseDates;
    
    @Override
    public ComparisonType getComparisonType() {
        return ComparisonType.TIMING;
    }
}