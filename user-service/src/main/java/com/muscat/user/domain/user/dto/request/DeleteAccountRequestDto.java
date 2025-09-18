package com.muscat.user.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeleteAccountRequestDto {
  @NotBlank(message = "비밀번호는 필수입니다")
  private String password;
}
