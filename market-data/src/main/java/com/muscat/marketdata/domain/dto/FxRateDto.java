package com.muscat.marketdata.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscat.marketdata.domain.entity.FxRate;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FX DTOs - KoreaExim 응답 아이템 DTO (외부 → 내부 변환 헬퍼 포함) - 내부 API 응답 DTO (FxRateResponse) - 동기화 요청 DTO
 * (FxSyncRequest)
 * <p>
 * 한 파일 전략: 공개 클래스 1개(FxRateDto) + 정적 내포 클래스.
 */
public class FxRateDto {

  // ===== 1) 외부: 한국수출입은행 환율 아이템 =====
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class KoreaEximRateItem {

    /**
     * 통화 단위 (예: "USD", "JPY(100)")
     */
    @JsonProperty("cur_unit")
    private String curUnit;

    /**
     * 매매기준율. 예: "1,390.50" (문자열, 콤마 포함)
     */
    @JsonProperty("deal_bas_r")
    private String dealBasR;

    /**
     * 통화명 (예: "미국 달러")
     */
    @JsonProperty("cur_nm")
    private String curNm;

    /**
     * 응답에 따라 제공될 수 있는 연/월/일 정보
     */
    @JsonProperty("yy")
    private String year;
    @JsonProperty("mm")
    private String month;
    @JsonProperty("dd")
    private String day;

    /**
     * v1 정책: USD 항목만 사용 (USD→KRW 저장, 역방향은 1/rate 계산)
     */
    @JsonIgnore
    public boolean isUsd() {
      return curUnit != null && curUnit.startsWith("USD");
    }

    /**
     * "1,390.50" → 1390.50 (콤마/공백/플러스/마이너스 방어)
     */
    @JsonIgnore
    public static BigDecimal parseAmount(String s) {
      if (s == null) {
        return null;
      }
      String cleaned = s.replace(",", "").trim();
      if (cleaned.isEmpty()) {
        return null;
      }
      // 예외 최소화: 숫자/소수점/부호만 허용
      if (!cleaned.matches("^[+-]?\\d+(\\.\\d+)?$")) {
        return null;
      }
      try {
        return new BigDecimal(cleaned);
      } catch (NumberFormatException e) {
        return null;
      }
    }

    /**
     * 응답 아이템 → 도메인 엔티티(FxRate) 변환. - v1: USD→KRW만 저장 - 날짜는 서비스/컨트롤러에서 주입
     */
    @JsonIgnore
    public FxRate toEntity(@NotNull LocalDate date) {
      Objects.requireNonNull(date, "date");
      if (!isUsd()) {
        return null;
      }
      BigDecimal rate = parseAmount(dealBasR);
      if (rate == null) {
        return null;
      }
      return FxRate.builder()
          .date(date)
          .rate(rate)
          .build();
    }

    /**
     * 응답에 연/월/일이 들어오는 케이스를 방어적으로 LocalDate로 구성 (없으면 null)
     */
    @JsonIgnore
    public LocalDate toLocalDateOrNull() {
      try {
        if (year == null || month == null || day == null) {
          return null;
        }
        String y = year.trim();
        String m = String.format("%02d", Integer.parseInt(month.trim()));
        String d = String.format("%02d", Integer.parseInt(day.trim()));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
        return LocalDate.parse(y + m + d, f);
      } catch (Exception ignore) {
        return null;
      }
    }
  }

  // ===== 2) 내부: 컨트롤러 응답 DTO =====
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class FxRateResponse {

    @NotNull
    private LocalDate date;

    /**
     * v1: USD 고정
     */
    @Builder.Default
    private String baseCcy = "USD";

    /**
     * v1: KRW 고정
     */
    @Builder.Default
    private String quoteCcy = "KRW";

    @NotNull
    private BigDecimal rate;

    public static FxRateResponse from(FxRate entity) {
      return FxRateResponse.builder()
          .date(entity.getDate())
          .rate(entity.getRate())
          .build();
    }
  }

  // ===== 3) 내부: 동기화 요청 DTO (기간 동기화 등) =====
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class FxSyncRequest {

    /**
     * 포함 시작일 (예: 2024-01-01)
     */
    @NotNull
    private LocalDate startDate;

    /**
     * 포함 종료일 (예: 2024-12-31)
     */
    @NotNull
    private LocalDate endDate;

    public void validateRange() {
      if (endDate.isBefore(startDate)) {
        throw new IllegalArgumentException("endDate must be on or after startDate");
      }
    }
  }
}
