package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "실제 투자 실행 요청")
@Data
public class InvestmentRequest {
    
    @Schema(description = "사용자 아이디", example = "user123", required = true)
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;
}