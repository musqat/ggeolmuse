package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import com.muscat.backtest.domain.model.StrategyParameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StrategyComparisonRequest extends BaseComparisonRequest {

  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @NotEmpty(message = "비교할 전략은 최소 1개 이상이어야 합니다")
  private List<StrategyParameter> strategies;

  @Override
  public ComparisonType getComparisonType() {
    return ComparisonType.STRATEGIES;
  }

}