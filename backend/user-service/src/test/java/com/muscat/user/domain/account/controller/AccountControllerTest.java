package com.muscat.user.domain.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.user.common.enums.response.AccountResponse;
import com.muscat.user.common.exception.AccountException;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.config.security.SecurityConfig;
import com.muscat.user.domain.account.dto.response.AccountResponseDto;
import com.muscat.user.domain.account.dto.response.AccountSummaryDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.response.ExchangeRateRangeDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// UserApplication 의 @ComponentScan 이 슬라이스 필터를 덮어써 전부 띄우려 한다. 띄울 클래스를 직접 고른다.
// 실제 보안 설정을 넣어 토큰 없는 요청이 막히는지도 본다
@WebMvcTest(AccountController.class)
@ContextConfiguration(classes = {AccountController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("AccountController 요청 · 응답")
class AccountControllerTest {

  private static final long USER_ID = 7L;
  private static final long ACCOUNT_ID = 3L;
  private static final String BASE = "/api/accounts";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AccountService accountService;

  @MockitoBean
  private AuthUtil authUtil;

  @MockitoBean
  private UserMapper userMapper;

  @BeforeEach
  void setUp() {
    given(authUtil.requireUserId(any(Jwt.class))).willReturn(USER_ID);
  }

  private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String exchangeBody(String from, String to) {
    return """
      {"fromCurrency":"%s","toCurrency":"%s","originalAmount":100,"exchangeRate":1300}
      """.formatted(from, to);
  }

  @Test
  @DisplayName("토큰이 없으면 401 이고 서비스까지 가지 않는다")
  void noToken_Unauthorized() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());

    verify(accountService, never()).getUserAccounts(anyLong());
  }

  @Test
  @DisplayName("계좌 생성은 토큰의 사용자로 만들고 201 과 계좌 정보를 돌려준다")
  void createAccount_Created() throws Exception {
    Account account = Account.builder().id(ACCOUNT_ID).build();
    given(accountService.createAccount(eq(USER_ID), any())).willReturn(account);
    given(userMapper.toAccountResponseDto(account)).willReturn(new AccountResponseDto(ACCOUNT_ID, "연습", "100-1",
      LocalDateTime.of(2026, 10, 1, 9, 0), BigDecimal.ZERO, BigDecimal.ZERO, null, null, new BigDecimal("0.001"), null));

    mockMvc.perform(json(post(BASE), """
        {"accountName":"연습","commissionRate":0.001}
        """))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(ACCOUNT_ID))
      .andExpect(jsonPath("$.accountName").value("연습"));

    verify(accountService).createAccount(eq(USER_ID), argThat(r -> "연습".equals(r.getAccountName())));
  }

  @Test
  @DisplayName("계좌명이 비거나 수수료율이 5% 를 넘으면 400 이고 만들지 않는다")
  void createAccount_Invalid_BadRequest() throws Exception {
    mockMvc.perform(json(post(BASE), """
        {"accountName":"","commissionRate":0.001}
        """))
      .andExpect(status().isBadRequest());
    mockMvc.perform(json(post(BASE), """
        {"accountName":"연습","commissionRate":0.06}
        """))
      .andExpect(status().isBadRequest());

    verify(accountService, never()).createAccount(anyLong(), any());
  }

  @Test
  @DisplayName("계좌 목록은 사용자의 계좌를 요약으로 돌려준다")
  void getUserAccounts_Ok() throws Exception {
    Account account = Account.builder().id(ACCOUNT_ID).build();
    given(accountService.getUserAccounts(USER_ID)).willReturn(List.of(account));
    given(userMapper.toAccountSummaryDto(account)).willReturn(new AccountSummaryDto(ACCOUNT_ID, "연습", "100-1",
      new BigDecimal("0.001"), new BigDecimal("12.50"), new BigDecimal("50000"), LocalDateTime.of(2026, 10, 1, 9, 0)));

    mockMvc.perform(get(BASE).with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].accountId").value(ACCOUNT_ID))
      .andExpect(jsonPath("$[0].usdBalance").value(12.50));
  }

  @Test
  @DisplayName("계좌 삭제 · 잔액 조회는 경로의 계좌와 토큰의 사용자로 부른다")
  void deleteAndBalance_UsePathAndUser() throws Exception {
    given(accountService.getAccountBalance(ACCOUNT_ID, USER_ID)).willReturn(new BalanceResponseDto("연습", "100-1",
      new BigDecimal("12.50"), new BigDecimal("50000"), null, null, null, null, null));

    mockMvc.perform(delete(BASE + "/" + ACCOUNT_ID).with(jwt())).andExpect(status().isOk());
    mockMvc.perform(get(BASE + "/" + ACCOUNT_ID + "/balance").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balanceUsd").value(12.50));

    verify(accountService).deleteAccount(ACCOUNT_ID, USER_ID);
  }

  @Test
  @DisplayName("입금은 금액을 넘기고, 1,000원 미만은 400 이다")
  void depositKrw() throws Exception {
    mockMvc.perform(json(post(BASE + "/" + ACCOUNT_ID + "/deposit"), """
        {"krwAmount":1000000}
        """))
      .andExpect(status().isOk());
    mockMvc.perform(json(post(BASE + "/" + ACCOUNT_ID + "/deposit"), """
        {"krwAmount":999}
        """))
      .andExpect(status().isBadRequest());

    verify(accountService).depositKrw(ACCOUNT_ID, USER_ID, new BigDecimal("1000000"));
  }

  @Test
  @DisplayName("환전은 방향에 맞는 서비스를 받은 환율로 부른다")
  void exchange_ByDirection() throws Exception {
    mockMvc.perform(json(post(BASE + "/" + ACCOUNT_ID + "/exchange"), exchangeBody("KRW", "USD")))
      .andExpect(status().isOk());
    mockMvc.perform(json(post(BASE + "/" + ACCOUNT_ID + "/exchange"), exchangeBody("USD", "KRW")))
      .andExpect(status().isOk());

    verify(accountService).exchangeKrwToUsd(ACCOUNT_ID, USER_ID, new BigDecimal("100"), new BigDecimal("1300"));
    verify(accountService).exchangeUsdToKrw(ACCOUNT_ID, USER_ID, new BigDecimal("100"), new BigDecimal("1300"));
  }

  @Test
  @DisplayName("지원하지 않는 통화 조합은 400 이고 잔액을 건드리지 않는다")
  void exchange_UnsupportedCurrency_BadRequest() throws Exception {
    mockMvc.perform(json(post(BASE + "/" + ACCOUNT_ID + "/exchange"), exchangeBody("KRW", "KRW")))
      .andExpect(status().isBadRequest());

    verify(accountService, never()).exchangeKrwToUsd(anyLong(), anyLong(), any(), any());
    verify(accountService, never()).exchangeUsdToKrw(anyLong(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("환율 조회: 현재 · 범위")
  void exchangeRates() throws Exception {
    given(accountService.getCurrentExchangeRate()).willReturn(new BigDecimal("1350.5"));
    given(accountService.getExchangeRateRange()).willReturn(new ExchangeRateRangeDto(new BigDecimal("1000"), new BigDecimal("1633")));

    mockMvc.perform(get(BASE + "/exchange-rates/current").with(jwt()))
      .andExpect(status().isOk()).andExpect(content().string("1350.5"));
    mockMvc.perform(get(BASE + "/exchange-rates/range").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.min").value(1000))
      .andExpect(jsonPath("$.max").value(1633));
  }

  @Test
  @DisplayName("현재 환율을 못 받으면 503 과 환율을 못 불러왔다는 문구를 준다")
  void currentExchangeRate_Unavailable_ServiceUnavailable() throws Exception {
    given(accountService.getCurrentExchangeRate())
      .willThrow(new AccountException(AccountResponse.EXCHANGE_RATE_SERVICE_ERROR));

    mockMvc.perform(get(BASE + "/exchange-rates/current").with(jwt()))
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.detail").value("환율을 불러오지 못했습니다."));
  }
}
