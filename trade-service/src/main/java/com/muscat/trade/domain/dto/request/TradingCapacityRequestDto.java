package com.muscat.trade.domain.dto.request;

import com.muscat.trade.common.constants.TradeConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "거래 가능 수량 조회 요청 정보")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingCapacityRequestDto {

  @Schema(description = "계좌 ID", example = "12345678", required = true)
  @NotBlank(message = "계좌 ID는 필수입니다")
  @Pattern(regexp = TradeConstants.ACCOUNT_ID_PATTERN, message = "유효하지 않은 계좌 ID 형식입니다")
  private String accountId;

  @Schema(description = "종목 심볼", example = "AAPL", required = true)
  @NotBlank(message = "종목 심볼은 필수입니다")
  @Pattern(regexp = TradeConstants.SYMBOL_PATTERN, message = "종목 심볼 형식이 올바르지 않습니다")
  private String symbol;

  @Schema(description = "거래 날짜", example = "2024-09-18", required = true)
  @NotNull(message = "거래 날짜는 필수입니다")
  private LocalDate tradeDate;
}
