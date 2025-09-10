package com.muscat.trade.domain.dto.request;

import com.muscat.trade.common.enums.type.PriceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.muscat.trade.common.constants.TradeConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class TradeRequestDto {

  @NotBlank(message = "계좌 ID는 필수입니다")
  @Pattern(regexp = TradeConstants.ACCOUNT_ID_PATTERN, message = "계좌 ID는 숫자만 허용됩니다")
  private String accountId; // 사용자가 선택한 계좌 ID

  @NotBlank(message = "종목 심볼은 필수입니다")
  @Size(min = TradeConstants.MIN_SYMBOL_LENGTH, max = TradeConstants.MAX_SYMBOL_LENGTH, message = "종목 심볼은 1-16자 이내여야 합니다")
  @Pattern(regexp = TradeConstants.SYMBOL_PATTERN, message = "종목 심볼은 대문자, 숫자, 마침표만 허용됩니다")
  private String symbol; // 주식 심볼 (AAPL, MSFT)

  @NotNull(message = "수량은 필수입니다")
  @DecimalMin(value = "0.000001", message = "수량은 0보다 커야 합니다")
  private BigDecimal quantity; // 거래 수량

  @NotNull(message = "거래일은 필수입니다")
  private LocalDate tradeDate; // 거래일

  private PriceType priceType = PriceType.CLOSE; // 가격 유형 (기본값: 종가)

  @DecimalMin(value = "0.01", message = "직접입력 가격은 0.01 이상이어야 합니다")
  private BigDecimal manualPrice; // 직접입력시 사용할 가격 (priceType이 MANUAL일 때만 필수)
}