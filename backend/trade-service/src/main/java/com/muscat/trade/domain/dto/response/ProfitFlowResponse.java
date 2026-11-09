package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "계좌 수익 흐름(거래일마다 원화 수익)")
public record ProfitFlowResponse(
  @Schema(description = "기간 안 거래일마다 원화 수익(오래된 날짜부터)")
  List<ProfitPoint> points,

  @Schema(description = "기간 안 수익이 가장 컸던 날(같으면 먼저 나온 날, 점이 없으면 null)")
  ProfitPoint best,

  @Schema(description = "기간 안 수익이 가장 작았던 날(같으면 먼저 나온 날, 점이 없으면 null)")
  ProfitPoint worst,

  @Schema(description = "마지막 거래일 원화 수익(점이 없으면 0)", example = "1250000")
  BigDecimal currentProfitKrw
) {

  @Schema(description = "거래일 하나의 원화 수익")
  public record ProfitPoint(
    @Schema(description = "거래일", example = "2024-09-18")
    LocalDate date,

    @Schema(description = "평가손익 + 실현 손익 + 받은 배당 − 수수료를 그 날짜 환율로 바꾼 원화", example = "1250000")
    BigDecimal profitKrw,

    @Schema(description = "그 날짜에 들고 있던 종목 매수 금액을 그 날짜 환율로 바꾼 원화(투자 원금)", example = "6024750")
    BigDecimal investedKrw
  ) {
  }
}
