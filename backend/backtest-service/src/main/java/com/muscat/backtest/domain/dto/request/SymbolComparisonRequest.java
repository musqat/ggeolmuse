package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "종목 간 비교 분석 요청")
@Data
@EqualsAndHashCode(callSuper = true)
public class SymbolComparisonRequest extends BaseComparisonRequest {
    
    @Schema(description = "비교할 종목 코드 목록", example = "[\"AAPL\", \"MSFT\", \"GOOGL\"]", required = true)
    @NotEmpty(message = "비교할 종목들은 최소 2개 이상이어야 합니다")
    private List<String> symbols;
    
    @Override
    public ComparisonType getComparisonType() {
        return ComparisonType.SYMBOLS;
    }
}