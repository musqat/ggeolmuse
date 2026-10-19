package com.muscat.marketdata.domain.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 일괄 비활성 결과(요청 수 · 처리 수)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteResponse {
    private int requested;
    private int deleted;
    private String message;
}
