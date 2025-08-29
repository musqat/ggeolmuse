package com.muscat.trade.common.responses;

import com.muscat.trade.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum HoldingResponse implements BaseResponseEnum {

  // 성공 응답 (200)
  HOLDINGS_FOUND("200", "보유종목 조회가 완료되었습니다."),
  HOLDING_DETAIL_FOUND("200", "보유종목 상세 조회가 완료되었습니다."),
  HOLDINGS_UPDATED("200", "보유종목이 업데이트되었습니다."),
  HOLDING_CREATED("200", "새로운 보유종목이 등록되었습니다."),
  HOLDING_DELETED("200", "보유종목이 삭제되었습니다."),
  AVERAGE_PRICE_CALCULATED("200", "평균단가 계산이 완료되었습니다."),
  PROFIT_LOSS_CALCULATED("200", "손익 계산이 완료되었습니다."),

  // 400 Bad Request
  INVALID_HOLDING_DATA("400", "보유종목 데이터가 유효하지 않습니다."),
  INVALID_QUANTITY("400", "수량이 유효하지 않습니다."),
  INVALID_AVERAGE_PRICE("400", "평균단가가 유효하지 않습니다."),
  NEGATIVE_QUANTITY("400", "수량은 음수일 수 없습니다."),
  ZERO_HOLDINGS("400", "보유 수량이 0입니다."),

  // 403 Forbidden
  HOLDINGS_ACCESS_DENIED("403", "보유종목에 접근할 권한이 없습니다."),

  // 404 Not Found
  HOLDINGS_NOT_FOUND("404", "보유종목을 찾을 수 없습니다."),
  HOLDING_NOT_FOUND("404", "해당 보유종목을 찾을 수 없습니다."),
  ACCOUNT_HOLDINGS_NOT_FOUND("404", "해당 계좌의 보유종목을 찾을 수 없습니다."),

  // 409 Conflict
  HOLDING_ALREADY_EXISTS("409", "이미 보유중인 종목입니다."),

  // 500 Internal Server Error
  HOLDINGS_UPDATE_FAILED("500", "보유종목 업데이트에 실패했습니다."),
  AVERAGE_PRICE_CALCULATION_ERROR("500", "평균단가 계산 중 오류가 발생했습니다."),
  PROFIT_LOSS_CALCULATION_ERROR("500", "손익 계산 중 오류가 발생했습니다."),
  HOLDINGS_SYNCHRONIZATION_ERROR("500", "보유종목 동기화 중 오류가 발생했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}