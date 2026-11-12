package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioAllocation;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.commonlib.util.MoneyUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "포트폴리오 백테스트 요청 (여러 종목을 현금 하나로 사고 평가)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRequest {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  @Schema(description = "시작일", example = "2020-01-01", required = true)
  private LocalDate startDate;

  @Schema(description = "종료일 (오늘 뒤면 오늘까지)", example = "2024-12-31", required = true)
  private LocalDate endDate;

  @Schema(description = "토큰의 subject 로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "줄 금액 방식 (AMOUNT 줄마다 금액, WEIGHT 총액 × 줄마다 비율, 비우면 AMOUNT)", example = "AMOUNT")
  private PortfolioAllocation allocation;

  @Schema(description = "총액 (WEIGHT 에서만, KRW, 적립식이면 회당 총액)", example = "10000000")
  private BigDecimal totalAmount;

  @Schema(description = "종목 줄 (1 ~ 10개, 결과 줄도 이 순서)", required = true)
  private List<PortfolioAsset> assets;

  @Schema(description = "매수 방식 (LUMP_SUM 줄마다 기간 안 첫 거래일에 금액 전부, DCA 줄마다 회차일에 회당 금액, 비우면 LUMP_SUM)", example = "LUMP_SUM")
  private PortfolioMethod method;

  @Schema(description = "적립식 매수 주기 (DCA 에서만, 모든 줄이 같은 주기, 비우면 MONTHLY)", example = "MONTHLY")
  private DcaFrequency frequency;

  @Schema(description = "적립식 첫 매수일 (시작일 ~ 종료일, 비우면 시작일)", example = "2020-01-15")
  private LocalDate firstPurchaseDate;

  @Schema(description = "매달 투자할 날짜 (1-31, MONTHLY 에서만, 비우면 첫 매수일의 날짜)", example = "15")
  private Integer purchaseDay;

  @Schema(description = "투자 주기 (개월 단위 1-12, MONTHLY 에서만, 비우면 1)", example = "1")
  private Integer investmentInterval;

  @Schema(description = "기간이 끝나면 (HOLD 끝까지 보유, SELL_AT_END 종목마다 마지막 거래일 종가에 전량 매도, 비우면 HOLD)", example = "HOLD")
  private PeriodEnd periodEnd;

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  @Positive(message = "매수 환율은 0보다 커야 합니다")
  private BigDecimal purchaseFxRate;

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  @Positive(message = "현재 환율은 0보다 커야 합니다")
  private BigDecimal currentFxRate;

  @Schema(description = "배당금 재투자 여부 (켜면 배당락일에 그 종목을 그 날짜 종가로 삼, 끄면 현금)", example = "false")
  private Boolean reinvestDividends;

  @Schema(description = "매매 수수료율 (0.25% = 0.0025, 매수 · 매도마다 차감)", example = "0.0025")
  @DecimalMin(value = "0", message = "수수료율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "수수료율은 0 이상 1 미만이어야 합니다")
  private BigDecimal tradingFeeRate;

  @Schema(description = "배당 원천징수세율 (미국 원천징수 15% = 0.15, 미적용 시 0)", example = "0.15")
  private BigDecimal dividendTaxRate;

  @Schema(description = "양도소득세 계산 · 납부 여부 (포트폴리오 전체 판 해마다 원화 실현 이익에서 연 250만원 공제 뒤 22% 를 다음 해 5월 1일 이후 첫 거래일에 현금에서 냄, 비우면 끔)", example = "true")
  private Boolean capitalGainsTax;

  // 줄 금액(금액 방식은 줄 금액, 비율 방식은 총액 × 비율 ÷ 100 을 원 단위로 반올림)
  public BigDecimal amountOf(PortfolioAsset asset) {
    if (PortfolioAllocation.orAmount(allocation) == PortfolioAllocation.AMOUNT) {
      return asset.amount();
    }
    return MoneyUtils.roundKrw(totalAmount.multiply(asset.weightPercent()).divide(HUNDRED, 8, RoundingMode.HALF_UP));
  }
}
