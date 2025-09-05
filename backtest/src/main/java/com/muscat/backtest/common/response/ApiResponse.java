package com.muscat.backtest.common.response;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 통합 API 응답 클래스
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

  private BacktestResponseCode status;
  private T data;

  public static <T> ApiResponse<T> of(BacktestResponseCode status, T data) {
    return new ApiResponse<>(status, data);
  }

  public static <T> ApiResponse<T> of(BacktestResponseCode status) {
    return new ApiResponse<>(status, null);
  }
}