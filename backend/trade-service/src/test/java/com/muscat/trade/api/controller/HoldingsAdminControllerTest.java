package com.muscat.trade.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.muscat.commonlib.filter.AdminAuditFilter;
import com.muscat.trade.common.exception.GlobalExceptionHandler;
import com.muscat.trade.config.security.AdminAccessConfig;
import com.muscat.trade.config.security.SecurityConfig;
import com.muscat.trade.domain.service.HoldingsBackfillService;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.AdminRoleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(HoldingsAdminController.class)
@ContextConfiguration(classes = {HoldingsAdminController.class, SecurityConfig.class, AdminAuditFilter.class,
  AdminAccessConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("보유 관리자 경로")
class HoldingsAdminControllerTest {

  private static final String BASE = "/api/admin/trade/holdings";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private HoldingsBackfillService holdingsBackfillService;

  @MockitoBean
  private UserServiceClient userServiceClient;

  @BeforeEach
  void activeAdmin() {
    given(userServiceClient.getAdminRole(anyString())).willReturn(new AdminRoleDto(true));
  }

  // Keycloak realm 역할 admin 이 권한 admin 으로 바뀐 토큰
  private static RequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("admin"));
  }

  @Test
  @DisplayName("토큰이 없으면 401, admin 권한이 없으면 403, 서비스 호출 없음")
  void requiresAdminAuthority() throws Exception {
    mockMvc.perform(post(BASE + "/rebuild")).andExpect(status().isUnauthorized());
    mockMvc.perform(post(BASE + "/rebuild").with(jwt())).andExpect(status().isForbidden());

    verifyNoInteractions(holdingsBackfillService);
  }

  @Test
  @DisplayName("토큰에 admin 권한이 남아 있어도 user-service 가 관리자가 아니라고 하면 403 ADMIN_ROLE_REQUIRED, 서비스 호출 없음")
  void demotedAdmin_Rejected() throws Exception {
    given(userServiceClient.getAdminRole("admin-1")).willReturn(new AdminRoleDto(false));

    mockMvc.perform(post(BASE + "/rebuild").with(jwt().jwt(token -> token.subject("admin-1"))
        .authorities(new SimpleGrantedAuthority("admin"))))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.errorCode").value("ADMIN_ROLE_REQUIRED"));

    verifyNoInteractions(holdingsBackfillService);
  }

  @Test
  @DisplayName("user-service 에 역할을 묻지 못하면 503 ADMIN_ROLE_UNAVAILABLE, 서비스 호출 없음")
  void roleLookupFailure_Unavailable() throws Exception {
    given(userServiceClient.getAdminRole(anyString())).willThrow(new IllegalStateException("user-service down"));

    mockMvc.perform(post(BASE + "/rebuild").with(admin()))
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.errorCode").value("ADMIN_ROLE_UNAVAILABLE"));

    verifyNoInteractions(holdingsBackfillService);
  }

  @Test
  @DisplayName("보유 다시 쌓기 응답은 바뀐 보유 행 수")
  void rebuildAll_ReturnsChangedCount() throws Exception {
    given(holdingsBackfillService.rebuildAll()).willReturn(2);

    mockMvc.perform(post(BASE + "/rebuild").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.changed").value(2));
  }

  @Test
  @DisplayName("관리자 쓰기 요청은 보안 필터가 읽은 토큰의 사용자로 기록된다")
  void rebuild_AuditedWithTokenUser() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(AdminAuditFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    given(holdingsBackfillService.rebuildAll()).willReturn(0);
    try {
      mockMvc.perform(post(BASE + "/rebuild").with(jwt()
          .jwt(token -> token.subject("admin-1").claim("email", "admin@example.com"))
          .authorities(new SimpleGrantedAuthority("admin"))))
        .andExpect(status().isOk());
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
      .startsWith("관리자 요청: actor=admin-1 POST /api/admin/trade/holdings/rebuild status=200"));
  }

  @Test
  @DisplayName("보안 체인이 401 · 403 으로 막은 관리자 쓰기 요청도 행위자 · 응답 코드와 같이 경고로 남긴다")
  void rejected_Audited() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(AdminAuditFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mockMvc.perform(post(BASE + "/rebuild")).andExpect(status().isUnauthorized());
      mockMvc.perform(post(BASE + "/rebuild").with(jwt().jwt(token -> token.subject("user-1"))))
        .andExpect(status().isForbidden());
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
      "관리자 요청: actor=anonymous POST /api/admin/trade/holdings/rebuild status=401 보안 체인에서 막음",
      "관리자 요청: actor=user-1 POST /api/admin/trade/holdings/rebuild status=403 보안 체인에서 막음");
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    verifyNoInteractions(holdingsBackfillService);
  }
}
