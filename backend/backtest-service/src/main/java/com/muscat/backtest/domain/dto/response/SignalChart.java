package com.muscat.backtest.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 매매 신호를 본 종목의 날마다 수정 종가 · 두 이동평균과 짧은 선이 긴 선을 넘은 날
@Schema(description = "매매 신호 차트(날마다 수정 종가 · 두 이동평균 · 교차한 날)")
public record SignalChart(
  @Schema(description = "이동평균을 본 종목", example = "AAPL")
  String symbol,
  @Schema(description = "시작일 ~ 평가일 쓸 수 있는 일봉마다 값(오래된 날부터)")
  List<Point> points,
  @Schema(description = "짧은 선이 긴 선을 넘은 날(보유와 상관없이 모두, 오래된 날부터, 없으면 빈 목록)")
  List<Cross> crosses
) {

  @Schema(description = "하루 수정 종가와 두 이동평균(N 거래일이 안 찬 평균은 칸 없음)")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Point(
    @Schema(description = "날짜", example = "2024-01-02")
    LocalDate date,
    @Schema(description = "이동평균에 넣은 값(수정 종가, 없거나 0 이하면 종가, USD)", example = "185.64")
    BigDecimal close,
    @Schema(description = "그 날짜까지 짧은 기간 거래일 평균(USD)", example = "183.12")
    BigDecimal shortAverage,
    @Schema(description = "그 날짜까지 긴 기간 거래일 평균(USD)", example = "178.40")
    BigDecimal longAverage
  ) {}

  @Schema(description = "교차한 날과 종류")
  public record Cross(
    @Schema(description = "날짜", example = "2024-03-04")
    LocalDate date,
    @Schema(description = "GOLDEN(짧은 선이 아래에서 위로) · DEAD(위에서 아래로)", example = "GOLDEN")
    CrossType type
  ) {}

  public enum CrossType { GOLDEN, DEAD }
}
