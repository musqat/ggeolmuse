package com.muscat.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.config.security.AdminAccessConfig;
import com.muscat.user.config.security.SecurityConfig;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.DividendSettlement;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// AccountControllerTest 와 같은 이유로 띄울 클래스를 직접 고른다. 관리자 경로는 실제 보안 설정이 admin 권한을 요구한다
@WebMvcTest(AdminUserController.class)
@ContextConfiguration(classes = {AdminUserController.class, SecurityConfig.class, GlobalExceptionHandler.class,
  AdminAccessConfig.class})
@ActiveProfiles("test")
@DisplayName("AdminUserController 요청 · 응답")
class AdminUserControllerTest {

  private static final long USER_ID = 5L;
  private static final String BASE = "/api/admin/users";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private AccountService accountService;

  @MockitoBean
  private AuthUtil authUtil;

  // Keycloak realm 역할 admin 이 권한 admin 으로 바뀐 토큰
  private static RequestPostProcessor admin() {
    return jwt().jwt(j -> j.subject("kc-admin")).authorities(new SimpleGrantedAuthority("admin"));
  }

  private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.with(admin()).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static User user() {
    return User.builder().id(USER_ID).email("user@example.com").nickname("투자자").role(UserRole.USER)
      .enabled(true).emailVerified(true).build();
  }

  @Test
  @DisplayName("토큰이 없으면 401, admin 권한이 없으면 403 이고 서비스까지 가지 않는다")
  void requiresAdminAuthority() throws Exception {
    mockMvc.perform(get(BASE + "/stats")).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/stats").with(jwt())).andExpect(status().isForbidden());

    verify(userService, never()).countTotalUsers();
  }

  @Test
  @DisplayName("목록은 정렬 파라미터를 읽고, 없으면 가입일 내림차순이다")
  void getAllUsers_ParsesSort() throws Exception {
    given(userService.getAllUsers(any())).willReturn(new PageImpl<>(List.of(user())));

    mockMvc.perform(get(BASE).param("page", "1").param("size", "10").param("sort", "email,asc").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].email").value("user@example.com"))
      .andExpect(jsonPath("$.content[0].username").value("투자자"));
    mockMvc.perform(get(BASE).with(admin())).andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(userService, times(2)).getAllUsers(pageable.capture());
    assertThat(pageable.getAllValues().get(0).getPageNumber()).isEqualTo(1);
    assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(10);
    assertThat(pageable.getAllValues().get(0).getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "email"));
    assertThat(pageable.getAllValues().get(1).getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  @DisplayName("상세는 사용자와 계좌 잔액을 같이 돌려준다")
  void getUser_WithAccounts() throws Exception {
    given(userService.findById(USER_ID)).willReturn(user());
    given(accountService.findAccountsByUserId(USER_ID)).willReturn(List.of(Account.builder().id(3L).accountName("연습")
      .balanceKrw(new BigDecimal("50000")).balanceUsd(new BigDecimal("12.50")).build()));

    mockMvc.perform(get(BASE + "/" + USER_ID).with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value("user@example.com"))
      .andExpect(jsonPath("$.accounts[0].accountId").value(3))
      .andExpect(jsonPath("$.accounts[0].balanceUsd").value(12.50));
  }

  @Test
  @DisplayName("역할 · 활성 · 닉네임 · 이메일 인증 변경은 바뀐 사용자를 돌려준다")
  void updates_ReturnUpdatedUser() throws Exception {
    given(userService.updateUserRole(USER_ID, UserRole.ADMIN, "kc-admin")).willReturn(user());
    given(userService.updateUserEnabled(USER_ID, false)).willReturn(user());
    given(userService.adminUpdateNickname(USER_ID, "새이름")).willReturn(user());
    given(userService.adminVerifyEmail(USER_ID)).willReturn(user());

    mockMvc.perform(json(patch(BASE + "/" + USER_ID + "/role"), """
        {"role":"ADMIN"}
        """))
      .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(USER_ID));
    mockMvc.perform(json(patch(BASE + "/" + USER_ID + "/enabled"), """
        {"enabled":false}
        """))
      .andExpect(status().isOk());
    mockMvc.perform(json(patch(BASE + "/" + USER_ID + "/nickname"), """
        {"nickname":"새이름"}
        """))
      .andExpect(status().isOk());
    mockMvc.perform(patch(BASE + "/" + USER_ID + "/verify-email").with(admin()))
      .andExpect(status().isOk());

    verify(userService).updateUserRole(USER_ID, UserRole.ADMIN, "kc-admin");
    verify(userService).updateUserEnabled(USER_ID, false);
    verify(userService).adminUpdateNickname(USER_ID, "새이름");
    verify(userService).adminVerifyEmail(USER_ID);
  }

  @Test
  @DisplayName("비밀번호 변경 · 강제 탈퇴는 경로의 사용자로 부른다")
  void passwordAndDelete() throws Exception {
    mockMvc.perform(json(patch(BASE + "/" + USER_ID + "/password"), """
        {"newPassword":"N3w-pass!"}
        """))
      .andExpect(status().isOk());
    mockMvc.perform(delete(BASE + "/" + USER_ID).with(admin())).andExpect(status().isOk());

    verify(userService).adminUpdatePassword(USER_ID, "N3w-pass!");
    verify(userService).adminDeleteUser(USER_ID);
  }

  @Test
  @DisplayName("통계의 비활성 수는 전체 − 활성이다")
  void stats_InactiveIsTotalMinusActive() throws Exception {
    given(userService.countTotalUsers()).willReturn(10L);
    given(userService.countActiveUsers()).willReturn(7L);
    given(userService.countAdminUsers()).willReturn(1L);

    mockMvc.perform(get(BASE + "/stats").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalUsers").value(10))
      .andExpect(jsonPath("$.inactiveUsers").value(3))
      .andExpect(jsonPath("$.adminUsers").value(1));

    verify(userService, never()).adminDeleteUser(anyLong());
  }

  @Test
  @DisplayName("배당 정산은 쪽마다 계좌 · 종목 · 배당락일 · 금액을 돌려주고 마지막 쪽인지 알린다")
  void getDividendSettlements() throws Exception {
    given(accountService.getDividendSettlements(1, 2)).willReturn(new PageImpl<>(List.of(DividendSettlement.of(
        20L, "AAPL", LocalDate.of(2024, 2, 9), new BigDecimal("2.03"), LocalDateTime.of(2026, 10, 18, 9, 0))),
      PageRequest.of(1, 2), 3));

    mockMvc.perform(get(BASE + "/dividend-settlements").param("page", "1").param("size", "2").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].accountId").value(20))
      .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
      .andExpect(jsonPath("$.content[0].exDate").value("2024-02-09"))
      .andExpect(jsonPath("$.content[0].amount").value(2.03))
      .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  @DisplayName("정지된 관리자는 토큰이 유효해도 관리자 API 를 쓰지 못한다")
  void disabledAdmin_Forbidden() throws Exception {
    willThrow(new UserException(UserResponse.ACCOUNT_DISABLED)).given(authUtil).requireUserId(any());

    mockMvc.perform(get(BASE + "/stats").with(admin())).andExpect(status().isForbidden());

    verify(userService, never()).countTotalUsers();
  }

  @Test
  @DisplayName("역할 없이 바꾸려 하면 400 이고 역할 변경을 부르지 않는다")
  void updateRole_WithoutRole_BadRequest() throws Exception {
    mockMvc.perform(json(patch(BASE + "/" + USER_ID + "/role"), "{}")).andExpect(status().isBadRequest());

    verify(userService, never()).updateUserRole(anyLong(), any(), any());
  }

  @Test
  @DisplayName("자기 역할을 바꾸려 하면 400 이다")
  void updateRole_Self_BadRequest() throws Exception {
    willThrow(new UserException(UserResponse.CANNOT_CHANGE_OWN_ROLE))
      .given(userService).updateUserRole(USER_ID, UserRole.USER, "kc-admin");

    mockMvc.perform(json(patch(BASE + "/" + USER_ID + "/role"), """
        {"role":"USER"}
        """))
      .andExpect(status().isBadRequest());
  }
}
