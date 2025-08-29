package com.muscat.trade.common.responses;

import com.muscat.trade.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PortfolioResponse implements BaseResponseEnum {

  // 성공 응답 (200)
  PORTFOLIO_FOUND("200", "포트폴리오 조회가 완료되었습니다."),
  PORTFOLIO_SUMMARY_FOUND("200", "포트폴리오 요약 조회가 완료되었습니다."),
  PORTFOLIO_PERFORMANCE_CALCULATED("200", "포트폴리오 성과 계산이 완료되었습니다."),
  PORTFOLIO_UPDATED("200", "포트폴리오가 업데이트되었습니다."),
  PORTFOLIO_ANALYSIS_COMPLETED("200", "포트폴리오 분석이 완료되었습니다."),
  ASSET_ALLOCATION_CALCULATED("200", "자산 배분 계산이 완료되었습니다."),
  DIVERSIFICATION_ANALYSIS_COMPLETED("200", "분산투자 분석이 완료되었습니다."),

  // 400 Bad Request
  EMPTY_PORTFOLIO("400", "포트폴리오가 비어있습니다."),
  INVALID_PORTFOLIO_PERIOD("400", "포트폴리오 조회 기간이 유효하지 않습니다."),
  INVALID_PERFORMANCE_METRIC("400", "성과 측정 지표가 유효하지 않습니다."),
  INSUFFICIENT_DATA_FOR_ANALYSIS("400", "분석에 필요한 데이터가 부족합니다."),

  // 403 Forbidden
  PORTFOLIO_ACCESS_DENIED("403", "포트폴리오에 접근할 권한이 없습니다."),

  // 404 Not Found
  PORTFOLIO_NOT_FOUND("404", "포트폴리오를 찾을 수 없습니다."),
  ACCOUNT_PORTFOLIO_NOT_FOUND("404", "해당 계좌의 포트폴리오를 찾을 수 없습니다."),
  PORTFOLIO_HISTORY_NOT_FOUND("404", "포트폴리오 히스토리를 찾을 수 없습니다."),

  // 503 Service Unavailable
  MARKET_DATA_UNAVAILABLE("503", "시장 데이터를 사용할 수 없습니다."),
  PRICING_SERVICE_UNAVAILABLE("503", "가격 정보 서비스를 사용할 수 없습니다."),

  // 500 Internal Server Error
  PORTFOLIO_CALCULATION_FAILED("500", "포트폴리오 계산에 실패했습니다."),
  PERFORMANCE_CALCULATION_FAILED("500", "성과 계산에 실패했습니다."),
  PORTFOLIO_UPDATE_FAILED("500", "포트폴리오 업데이트에 실패했습니다."),
  PORTFOLIO_SYNC_FAILED("500", "포트폴리오 동기화에 실패했습니다."),
  ALLOCATION_CALCULATION_FAILED("500", "자산 배분 계산에 실패했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}