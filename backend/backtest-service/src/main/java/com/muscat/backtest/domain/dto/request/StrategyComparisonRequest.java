package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import com.muscat.backtest.domain.model.StrategyParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "투자 전략 비교 분석 요청")
@Data
@EqualsAndHashCode(callSuper = true)
public class StrategyComparisonRequest extends BaseComparisonRequest {

  @Schema(description = "비교할 종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "비교할 전략 매개변수 목록", required = true)
  @NotEmpty(message = "비교할 전략은 최소 1개 이상이어야 합니다")
  private List<StrategyParameter> strategies;

  @Override
  public ComparisonType getComparisonType() {
    return ComparisonType.STRATEGIES;
  }

}