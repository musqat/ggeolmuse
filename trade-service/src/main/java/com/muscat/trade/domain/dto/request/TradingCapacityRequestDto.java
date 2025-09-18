package com.muscat.trade.domain.dto.request;

import com.muscat.trade.common.constants.TradeConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class TradingCapacityRequestDto {

    @NotBlank(message = "계좌 ID는 필수입니다")
    @Pattern(regexp = TradeConstants.ACCOUNT_ID_PATTERN, message = "유효하지 않은 계좌 ID 형식입니다")
    private String accountId;

    @NotBlank(message = "종목 심볼은 필수입니다")
    @Pattern(regexp = TradeConstants.SYMBOL_PATTERN, message = "종목 심볼 형식이 올바르지 않습니다")
    private String symbol;

    @NotNull(message = "거래 날짜는 필수입니다")
    private LocalDate tradeDate;
}