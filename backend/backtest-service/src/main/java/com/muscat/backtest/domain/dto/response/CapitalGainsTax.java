package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 매도 실현 이익의 연도별 양도소득세 · 기간 안에 낸 양도세와 세후 평가
@Schema(description = "양도소득세(결제된 해마다 원화 실현 이익 · 공제 · 세금 · 납부)와 세후 평가")
public record CapitalGainsTax(
  @Schema(description = "판 거래가 결제된 해마다 실현 이익 · 공제 · 세금 · 납부(해 순서, 판 거래가 없으면 빈 목록)")
  List<Year> years,
  @Schema(description = "양도세 합 (KRW)", example = "330000")
  BigDecimal totalTaxKrw,
  @Schema(description = "기간 안에 판 돈에서 낸 양도세 합 (KRW)", example = "330000")
  BigDecimal paidTaxKrw,
  @Schema(description = "남은 양도세 (양도세 합 - 낸 양도세, 납부일이 기간 뒤인 해와 현금이 모자라 못 낸 몫, KRW)", example = "0")
  BigDecimal unpaidTaxKrw,
  @Schema(description = "현금 (총 자산 - 주식 평가, 판 돈 남은 현금 + 현금 배당, 낸 양도세 뺌, KRW)", example = "1200000")
  BigDecimal cashKrw,
  @Schema(description = "주식 평가 (평가일 환율, KRW)", example = "8470000")
  BigDecimal stockKrw,
  @Schema(description = "세후 총 평가 (총 자산 - 남은 양도세, KRW)", example = "9670000")
  BigDecimal afterTaxValueKrw,
  @Schema(description = "세후 수익률 (%, 총 수익률과 같은 기준 금액 대비)", example = "20.88")
  BigDecimal afterTaxReturnPercent,
  @Schema(description = "연말 정리와 기간 끝에 한 번에 판 양도세 비교 (연말 정리를 켜고 기간이 2년 이상일 때만, 아니면 null)")
  HarvestComparison harvestComparison
) {

  // 연말 정리 비교 없는 양도세
  public CapitalGainsTax(List<Year> years, BigDecimal totalTaxKrw, BigDecimal paidTaxKrw, BigDecimal unpaidTaxKrw,
    BigDecimal cashKrw, BigDecimal stockKrw, BigDecimal afterTaxValueKrw, BigDecimal afterTaxReturnPercent) {
    this(years, totalTaxKrw, paidTaxKrw, unpaidTaxKrw, cashKrw, stockKrw, afterTaxValueKrw, afterTaxReturnPercent,
      null);
  }

  // 연말 정리 비교를 더한 사본
  public CapitalGainsTax withHarvestComparison(HarvestComparison comparison) {
    return new CapitalGainsTax(years, totalTaxKrw, paidTaxKrw, unpaidTaxKrw, cashKrw, stockKrw, afterTaxValueKrw,
      afterTaxReturnPercent, comparison);
  }

  @Schema(description = "같은 요청을 기간 끝에 판다고 보고 연말 정리를 끈 양도세와 켠 양도세")
  public record HarvestComparison(
    @Schema(description = "연말 정리 없이 기간 끝에 한 번에 판 양도세 합 (KRW)", example = "4026000")
    BigDecimal sellAtEndTaxKrw,
    @Schema(description = "연말 정리하고 기간 끝에 판 양도세 합 (KRW)", example = "2376000")
    BigDecimal harvestTaxKrw,
    @Schema(description = "연말 정리로 아낀 양도세 (앞 둘의 차, 음수면 더 냄, KRW)", example = "1650000")
    BigDecimal savedKrw
  ) {

    // 기간 끝에 한 번에 판 양도세 - 연말 정리한 양도세
    public static HarvestComparison of(BigDecimal sellAtEndTaxKrw, BigDecimal harvestTaxKrw) {
      return new HarvestComparison(sellAtEndTaxKrw, harvestTaxKrw, sellAtEndTaxKrw.subtract(harvestTaxKrw));
    }
  }

  @Schema(description = "한 해 양도세와 납부")
  public record Year(
    @Schema(description = "결제된 연도", example = "2024")
    int year,
    @Schema(description = "그해 결제된 원화 실현 이익 합 (손실은 그해 이익과 상계, KRW)", example = "4000000")
    BigDecimal realizedGainKrw,
    @Schema(description = "기본 공제 (이익만큼 연 250만원까지, 이익이 없으면 0, KRW)", example = "2500000")
    BigDecimal deductionKrw,
    @Schema(description = "양도세 ((실현 이익 - 공제) × 22%, 원 미만 버림, KRW)", example = "330000")
    BigDecimal taxKrw,
    @Schema(description = "납부일 (다음 해 5월 1일 이후 첫 거래일, 기간 안에 오지 않았으면 null)", example = "2025-05-01")
    LocalDate dueDate,
    @Schema(description = "그 해 세금 가운데 기간 안에 낸 양도세 (KRW, 없으면 0)", example = "330000")
    BigDecimal paidKrw
  ) {}
}
