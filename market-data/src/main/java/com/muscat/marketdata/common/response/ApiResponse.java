package com.muscat.marketdata.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private String statusCode;    // 상태 코드 (200, 400, 500 등)
    private String statusMsg;     // 상태 메시지
    private T data;               // 응답 데이터

    // 성공 응답 생성 - trade 모듈과 호환되는 방식
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .statusCode("200")
                .statusMsg("SUCCESS")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .statusCode("200")
                .statusMsg(message)
                .data(data)
                .build();
    }

    // 에러 응답 생성
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .statusCode(code)
                .statusMsg(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return ApiResponse.<T>builder()
                .statusCode(code)
                .statusMsg(message)
                .data(data)
                .build();
    }
}