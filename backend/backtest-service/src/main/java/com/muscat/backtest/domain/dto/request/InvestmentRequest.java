package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "실제 투자 실행 요청")
@Data
public class InvestmentRequest {
    
    @Schema(description = "토큰의 subject 로 채움", accessMode = Schema.AccessMode.READ_ONLY)
    private String userId;
}