package com.muscat.trade.common.responses;

import com.muscat.trade.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

  private String statusCode;    // 상태 코드 (200, 400, 500 등)
  private String statusMsg;     // 상태 메시지
  private T data;               // 응답 데이터

  public ApiResponse(BaseResponseEnum response) {
    this.statusCode = response.getCode();
    this.statusMsg = response.getMessage();
  }

  public ApiResponse(BaseResponseEnum response, T data) {
    this(response);
    this.data = data;
  }

  // 성공 응답 생성
  public static <T> ApiResponse<T> success(BaseResponseEnum response) {
    return new ApiResponse<>(response);
  }

  public static <T> ApiResponse<T> success(BaseResponseEnum response, T data) {
    return new ApiResponse<>(response, data);
  }

  // 에러 응답 생성
  public static <T> ApiResponse<T> error(BaseResponseEnum response) {
    return new ApiResponse<>(response);
  }

  public static <T> ApiResponse<T> error(BaseResponseEnum response, T data) {
    return new ApiResponse<>(response, data);
  }

  // 성공 여부 확인
  public boolean isSuccess() {
    return "200".equals(statusCode) || "201".equals(statusCode);
  }
}