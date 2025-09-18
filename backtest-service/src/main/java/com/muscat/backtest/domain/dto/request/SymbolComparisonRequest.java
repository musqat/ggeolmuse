package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SymbolComparisonRequest extends BaseComparisonRequest {
    
    @NotEmpty(message = "비교할 종목들은 최소 2개 이상이어야 합니다")
    private List<String> symbols;
    
    @Override
    public ComparisonType getComparisonType() {
        return ComparisonType.SYMBOLS;
    }
}