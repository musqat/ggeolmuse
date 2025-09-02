package com.muscat.trade.infra.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserApiResponse<T> {
  
  private String statusCode;    // User 서비스 응답 구조
  private String statusMsg;     // User 서비스 응답 구조  
  private T data;               // 응답 데이터

}