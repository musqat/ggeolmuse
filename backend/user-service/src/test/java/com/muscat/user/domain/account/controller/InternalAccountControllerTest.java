package com.muscat.user.domain.account.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.commonlib.security.ServiceSecurity;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.config.security.SecurityConfig;
import com.muscat.user.domain.account.dto.response.ExchangeRecordDto;
import com.muscat.user.domain.account.service.AccountHistoryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InternalAccountController.class)
@ContextConfiguration(classes = {InternalAccountController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("InternalAccountController 요청 · 응답")
class InternalAccountControllerTest {

  private static final long USER_ID = 7L;
  private static final String KEYCLOAK_ID = "kc-7";
  private static final String EXCHANGES = "/api/internal/accounts/exchanges";
  private static final RequestPostProcessor SERVICE =
    jwt().authorities(new SimpleGrantedAuthority(ServiceSecurity.SERVICE_ROLE));

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AccountHistoryService accountHistoryService;

  @MockitoBean
  private AuthUtil authUtil;

  @BeforeEach
  void setUp() {
    given(authUtil.requireUserId(KEYCLOAK_ID)).willReturn(USER_ID);
  }

  @Test
  @DisplayName("토큰이 없으면 401, 서비스 역할이 없는 사용자 토큰은 403 이고 서비스까지 가지 않는다")
  void withoutServiceToken_Rejected() throws Exception {
    mockMvc.perform(get(EXCHANGES).param("keycloakId", KEYCLOAK_ID)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(EXCHANGES).param("keycloakId", KEYCLOAK_ID).with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
      .andExpect(status().isForbidden());

    verify(accountHistoryService, never()).getExchangeRecords(anyLong());
  }

  @Test
  @DisplayName("사용자 Keycloak ID 가 없으면 400 이고 서비스까지 가지 않는다")
  void missingKeycloakId_BadRequest() throws Exception {
    mockMvc.perform(get(EXCHANGES).with(SERVICE)).andExpect(status().isBadRequest());

    verify(accountHistoryService, never()).getExchangeRecords(anyLong());
  }

  @Test
  @DisplayName("환전 기록은 받은 Keycloak ID 의 사용자로 찾아 계좌 · 시각 · 통화 · 금액 · 환율 칸으로 돌려준다")
  void exchanges_OfGivenUser() throws Exception {
    given(accountHistoryService.getExchangeRecords(USER_ID)).willReturn(List.of(
      new ExchangeRecordDto(3L, LocalDateTime.of(2024, 9, 2, 10, 15, 30), "KRW", "USD",
        new BigDecimal("1300000.00"), new BigDecimal("1000.00"), new BigDecimal("1300.000000")),
      new ExchangeRecordDto(4L, LocalDateTime.of(2024, 10, 1, 9, 0, 5), "USD", "KRW",
        new BigDecimal("300.00"), new BigDecimal("405000.00"), new BigDecimal("1350.000000"))));

    mockMvc.perform(get(EXCHANGES).param("keycloakId", KEYCLOAK_ID).with(SERVICE))
      .andExpect(status().isOk())
      .andExpect(content().json("""
        [{"accountId":3,"exchangedAt":"2024-09-02T10:15:30","fromCurrency":"KRW","toCurrency":"USD",
          "originalAmount":1300000.00,"exchangedAmount":1000.00,"exchangeRate":1300.000000},
         {"accountId":4,"exchangedAt":"2024-10-01T09:00:05","fromCurrency":"USD","toCurrency":"KRW",
          "originalAmount":300.00,"exchangedAmount":405000.00,"exchangeRate":1350.000000}]
        """, true));
  }

  @Test
  @DisplayName("환전 시각은 초까지만 돌려준다")
  void exchanges_ExchangedAtToSeconds() throws Exception {
    given(accountHistoryService.getExchangeRecords(USER_ID)).willReturn(List.of(
      new ExchangeRecordDto(3L, LocalDateTime.of(2024, 9, 2, 10, 15, 30, 123_456_000), "KRW", "USD",
        new BigDecimal("1300000.00"), new BigDecimal("1000.00"), new BigDecimal("1300.000000"))));

    mockMvc.perform(get(EXCHANGES).param("keycloakId", KEYCLOAK_ID).with(SERVICE))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].exchangedAt").value("2024-09-02T10:15:30"));
  }

  @Test
  @DisplayName("환전 기록이 없으면 빈 배열")
  void exchanges_Empty() throws Exception {
    given(accountHistoryService.getExchangeRecords(USER_ID)).willReturn(List.of());

    mockMvc.perform(get(EXCHANGES).param("keycloakId", KEYCLOAK_ID).with(SERVICE))
      .andExpect(status().isOk())
      .andExpect(content().json("[]", true));
  }
}
