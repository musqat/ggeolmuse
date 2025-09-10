package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TimingComparisonRequest extends BaseComparisonRequest {
    
    @NotBlank(message = "종목 코드는 필수입니다")
    private String symbol;
    
    @NotEmpty(message = "비교할 매수 시점은 최소 2개 이상이어야 합니다")
    private List<LocalDate> purchaseDates;
    
    @Override
    public ComparisonType getComparisonType() {
        return ComparisonType.TIMING;
    }
}