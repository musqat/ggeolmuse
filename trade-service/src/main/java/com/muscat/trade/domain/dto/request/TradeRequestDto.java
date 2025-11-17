package com.muscat.trade.domain.dto.request;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.type.PriceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "거래 요청 정보")
public class TradeRequestDto {

  @Schema(description = "계좌 ID", example = "12345", required = true)
  @NotBlank(message = "계좌 ID는 필수입니다")
  @Pattern(regexp = TradeConstants.ACCOUNT_ID_PATTERN, message = "계좌 ID는 숫자만 허용됩니다")
  private String accountId;

  @Schema(description = "종목 심볼", example = "AAPL", required = true)
  @NotBlank(message = "종목 심볼은 필수입니다")
  @Size(min = TradeConstants.MIN_SYMBOL_LENGTH, max = TradeConstants.MAX_SYMBOL_LENGTH, message = "종목 심볼은 1-16자 이내여야 합니다")
  @Pattern(regexp = TradeConstants.SYMBOL_PATTERN, message = "종목 심볼은 대문자, 숫자, 마침표만 허용됩니다")
  private String symbol;

  @Schema(description = "거래 수량", example = "10.5", required = true)
  @NotNull(message = "수량은 필수입니다")
  @DecimalMin(value = "0.000001", message = "수량은 0보다 커야 합니다")
  private BigDecimal quantity;

  @Schema(description = "거래일", example = "2024-09-18", required = true)
  @NotNull(message = "거래일은 필수입니다")
  private LocalDate tradeDate;

  @Schema(description = "가격 유형", example = "CLOSE", allowableValues = {"OPEN", "HIGH", "LOW",
    "CLOSE", "MANUAL"})
  private PriceType priceType = PriceType.CLOSE;

  @Schema(description = "직접입력 가격 (priceType이 MANUAL일 때 필수)", example = "238.15")
  @DecimalMin(value = "0.01", message = "직접입력 가격은 0.01 이상이어야 합니다")
  private BigDecimal manualPrice;
}
