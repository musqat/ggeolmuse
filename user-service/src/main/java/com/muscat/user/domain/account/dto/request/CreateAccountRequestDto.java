package com.muscat.user.domain.account.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccountRequestDto {

  @NotBlank(message = "계좌명을 입력해주세요")
  @Size(max = 50)
  private String accountName;  // 계좌 이름

  @NotNull(message = "수수료율을 입력해주세요")
  @DecimalMin(value = "0")
  @DecimalMax(value = "0.05")
  private BigDecimal commissionRate;  // 수수료율 (0~5%)
}