package com.muscat.user.common.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscat.user.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

  @JsonProperty("statusCode")
  private String statusCode;    // 상태 코드 (200, 400, 500 등)
  
  @JsonProperty("statusMsg") 
  private String statusMsg;     // 상태 메시지
  
  @JsonProperty("data")
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
}