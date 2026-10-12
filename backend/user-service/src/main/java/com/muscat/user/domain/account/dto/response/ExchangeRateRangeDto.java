package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "환전에 쓸 수 있는 환율 범위")
public record ExchangeRateRangeDto(
  @Schema(description = "허용 최저 환율 (원)", example = "1000")
  BigDecimal min,

  @Schema(description = "허용 최고 환율 (원)", example = "1633")
  BigDecimal max
) {

}
