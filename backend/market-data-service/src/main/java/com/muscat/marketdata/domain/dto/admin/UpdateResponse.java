package com.muscat.marketdata.domain.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 관리자 갱신 요청의 안내 문구
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResponse {
    private String message;

    public static UpdateResponse of(String message) {
        return new UpdateResponse(message);
    }
}
