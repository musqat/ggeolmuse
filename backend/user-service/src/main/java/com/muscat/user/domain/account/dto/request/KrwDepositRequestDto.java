package com.muscat.user.domain.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "KRW 입금 요청 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KrwDepositRequestDto {

  @Schema(description = "KRW 입금액", example = "50000.00", required = true)
  @NotNull(message = "입금액을 입력해주세요")
  @DecimalMin(value = "1000", message = "최소 입금액은 1,000원입니다")
  private BigDecimal krwAmount;  // KRW 입금액
}
