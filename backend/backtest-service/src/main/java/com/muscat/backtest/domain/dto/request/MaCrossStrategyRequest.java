package com.muscat.backtest.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.type.ExecutionTiming;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.enums.type.TaxSale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Schema(description = "이동평균 교차 투자 전략 요청")
@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class MaCrossStrategyRequest extends RequestOptions implements SignalStrategyRequest {

  @Schema(description = "종목 코드 (사고파는 종목, 지수는 안 됨)", example = "AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일 (이 날짜부터 교차를 봄)", example = "2024-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-09-18", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "토큰의 subject 로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "원금: 첫 골든 크로스에 전부 넣는 돈 (KRW, 첫 골든 크로스 뒤 매수는 판 돈 전부)", example = "10000000.00", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "투자금액은 필수입니다")
  @Positive(message = "투자금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  @Schema(description = "짧은 이동평균 기간 (거래일 2 ~ 250, 긴 기간보다 작게, 비우면 20)", example = "20")
  private Integer shortWindow;

  @Schema(description = "긴 이동평균 기간 (거래일 2 ~ 250, 비우면 60)", example = "60")
  private Integer longWindow;

  @Schema(description = "양도세 낼 현금이 모자랄 때 (NONE 현금이 생길 때 냄, SELL 납부일 종가에 모자란 만큼 팔아 냄, 비우면 NONE)", example = "SELL")
  private TaxSale taxSale;

  @Schema(description = "신호 종목: 이동평균 교차를 보는 종목 (지수 ^GSPC · ^NDX 도 됨, 비우면 종목 코드)", example = "^GSPC")
  private String signalSymbol;

  @Schema(description = "트레일링 스탑 하락 비율: 종가가 기준가(사이클 첫 매수가에서 시작한 종가 고점, 판 뒤에는 판 값)보다 이만큼 내리면 팜, 다 팔면 다음 골든 크로스에 다시 삼 (10% = 0.1, 0 초과 1 미만, 비우면 안 함)", example = "0.1")
  private BigDecimal trailingStopRate;

  @Schema(description = "트레일링 스탑 팔 비율: 종가가 기준가보다 하락 비율만큼 내릴 때마다 보유의 이만큼 팜 (50% = 0.5, 0 초과 1 이하, 비우면 전량, 하락 비율과 함께)", example = "0.5")
  private BigDecimal trailingSellRate;

  @Schema(description = "부분 익절 단계: 수익률이 낮은 단계부터 사이클에 한 번씩, 하루에 한 단계 보유의 팔 비율만큼 팜, 다 팔면 다음 골든 크로스에 다시 삼 (5단계까지, 비우면 안 함)")
  private List<PartialTakeProfit> partialTakeProfits;

  @Schema(description = "체결 시점 (SAME_DAY_CLOSE 골든 · 데드 크로스 난 날 종가, NEXT_DAY_OPEN 그다음 거래일 시가(다음 거래일이 없으면 체결 안 함), 비우면 SAME_DAY_CLOSE)", example = "NEXT_DAY_OPEN")
  private ExecutionTiming executionTiming;

  // 이력 저장 JSON 에는 넣지 않음
  @JsonIgnore
  @Override
  public StrategyType getStrategyType() {
    return StrategyType.MA_CROSS;
  }

  // 짧은 기간(비우면 20)
  public int shortWindowOrDefault() {
    return shortWindow != null ? shortWindow : MovingAverage.DEFAULT_SHORT_WINDOW;
  }

  // 긴 기간(비우면 60)
  public int longWindowOrDefault() {
    return longWindow != null ? longWindow : MovingAverage.DEFAULT_LONG_WINDOW;
  }

  @Override
  public MaCrossStrategyRequest yearEndVariant(boolean harvest) {
    return toBuilder().yearEndHarvest(harvest).periodEnd(PeriodEnd.SELL_AT_END).userId(null).build();
  }
}
