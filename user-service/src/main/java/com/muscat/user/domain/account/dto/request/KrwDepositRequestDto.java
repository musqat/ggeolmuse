package com.muscat.user.domain.account.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KrwDepositRequestDto {

  @NotNull(message = "입금액을 입력해주세요")
  @DecimalMin(value = "1000", message = "최소 입금액은 1,000원입니다")
  private BigDecimal krwAmount;  // KRW 입금액
}
