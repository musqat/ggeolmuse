package com.muscat.user.api.controller;

import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.util.AuthorizationUtil;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 사용자 관리 API
 * 사용자 목록 조회, 역할 변경, 계좌 조회 등의 관리 기능을 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final UserService userService;
  private final AccountService accountService;
  private final AuthorizationUtil authorizationUtil;

  /**
   * 전체 사용자 목록 조회 (페이징)
   */
  @GetMapping
  public ResponseEntity<Page<UserSummary>> getAllUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

    authorizationUtil.requireAdmin();

    log.info("전체 사용자 목록 조회: page={}, size={}", page, size);

    // Sort 파라미터 파싱
    Sort.Direction direction = sort.length > 1 && "asc".equalsIgnoreCase(sort[1])
      ? Sort.Direction.ASC
      : Sort.Direction.DESC;
    String property = sort[0];

    Pageable pageable = PageRequest.of(page, size, Sort.by(direction, property));
    Page<User> users = userService.getAllUsers(pageable);

    Page<UserSummary> userSummaries = users.map(user -> UserSummary.builder()
      .userId(user.getId())
      .email(user.getEmail())
      .username(user.getNickname())
      .role(user.getRole())
      .enabled(user.isEnabled())
      .emailVerified(user.isEmailVerified())
      .createdAt(user.getCreatedAt())
      .lastLoginAt(user.getLastLoginAt())
      .build());

    return ResponseEntity.ok(userSummaries);
  }

  /**
   * 특정 사용자 상세 조회
   */
  @GetMapping("/{userId}")
  public ResponseEntity<UserDetail> getUser(@PathVariable Long userId) {
    authorizationUtil.requireAdmin();

    log.info("사용자 상세 조회: userId={}", userId);

    User user = userService.findById(userId);
    List<Account> accounts = accountService.findAccountsByUserId(userId);

    UserDetail detail = UserDetail.builder()
      .userId(user.getId())
      .email(user.getEmail())
      .username(user.getNickname())
      .role(user.getRole())
      .enabled(user.isEnabled())
      .emailVerified(user.isEmailVerified())
      .createdAt(user.getCreatedAt())
      .updatedAt(user.getUpdatedAt())
      .lastLoginAt(user.getLastLoginAt())
      .accounts(accounts.stream()
        .map(account -> AccountSummary.builder()
          .accountId(account.getId())
          .accountName(account.getAccountName())
          .balanceKrw(account.getBalanceKrw())
          .balanceUsd(account.getBalanceUsd())
          .createdAt(account.getCreatedAt())
          .build())
        .toList())
      .build();

    return ResponseEntity.ok(detail);
  }

  /**
   * 사용자 역할 변경
   */
  @PatchMapping("/{userId}/role")
  public ResponseEntity<UserSummary> updateUserRole(
    @PathVariable Long userId,
    @RequestBody UpdateRoleRequest request) {

    authorizationUtil.requireAdmin();

    log.info("사용자 역할 변경: userId={}, newRole={}", userId, request.getRole());

    User updatedUser = userService.updateUserRole(userId, request.getRole());

    UserSummary summary = UserSummary.builder()
      .userId(updatedUser.getId())
      .email(updatedUser.getEmail())
      .username(updatedUser.getNickname())
      .role(updatedUser.getRole())
      .enabled(updatedUser.isEnabled())
      .emailVerified(updatedUser.isEmailVerified())
      .createdAt(updatedUser.getCreatedAt())
      .lastLoginAt(updatedUser.getLastLoginAt())
      .build();

    return ResponseEntity.ok(summary);
  }

  /**
   * 사용자 활성화/비활성화
   */
  @PatchMapping("/{userId}/enabled")
  public ResponseEntity<UserSummary> updateUserEnabled(
    @PathVariable Long userId,
    @RequestBody UpdateEnabledRequest request) {

    authorizationUtil.requireAdmin();

    log.info("사용자 활성화 상태 변경: userId={}, enabled={}", userId, request.isEnabled());

    User updatedUser = userService.updateUserEnabled(userId, request.isEnabled());

    UserSummary summary = UserSummary.builder()
      .userId(updatedUser.getId())
      .email(updatedUser.getEmail())
      .username(updatedUser.getNickname())
      .role(updatedUser.getRole())
      .enabled(updatedUser.isEnabled())
      .emailVerified(updatedUser.isEmailVerified())
      .createdAt(updatedUser.getCreatedAt())
      .lastLoginAt(updatedUser.getLastLoginAt())
      .build();

    return ResponseEntity.ok(summary);
  }

  /**
   * 사용자 통계 조회
   */
  @GetMapping("/stats")
  public ResponseEntity<UserStats> getUserStats() {
    authorizationUtil.requireAdmin();

    log.info("사용자 통계 조회");

    long totalUsers = userService.countTotalUsers();
    long activeUsers = userService.countActiveUsers();
    long adminUsers = userService.countAdminUsers();

    UserStats stats = UserStats.builder()
      .totalUsers(totalUsers)
      .activeUsers(activeUsers)
      .adminUsers(adminUsers)
      .inactiveUsers(totalUsers - activeUsers)
      .build();

    return ResponseEntity.ok(stats);
  }

  /**
   * 강제 닉네임 변경
   */
  @PatchMapping("/{userId}/nickname")
  public ResponseEntity<UserSummary> updateNickname(
    @PathVariable Long userId,
    @RequestBody UpdateNicknameRequest request) {

    authorizationUtil.requireAdmin();

    log.info("Admin이 사용자 닉네임 강제 변경: userId={}, newNickname={}", userId, request.getNickname());

    User updatedUser = userService.adminUpdateNickname(userId, request.getNickname());

    UserSummary summary = UserSummary.builder()
      .userId(updatedUser.getId())
      .email(updatedUser.getEmail())
      .username(updatedUser.getNickname())
      .role(updatedUser.getRole())
      .enabled(updatedUser.isEnabled())
      .emailVerified(updatedUser.isEmailVerified())
      .createdAt(updatedUser.getCreatedAt())
      .lastLoginAt(updatedUser.getLastLoginAt())
      .build();

    return ResponseEntity.ok(summary);
  }

  /**
   * 강제 비밀번호 변경
   */
  @PatchMapping("/{userId}/password")
  public ResponseEntity<Void> updatePassword(
    @PathVariable Long userId,
    @RequestBody UpdatePasswordRequest request) {

    authorizationUtil.requireAdmin();

    log.info("Admin이 사용자 비밀번호 강제 변경: userId={}", userId);

    userService.adminUpdatePassword(userId, request.getNewPassword());

    return ResponseEntity.ok().build();
  }

  /**
   * 이메일 강제 인증
   */
  @PatchMapping("/{userId}/verify-email")
  public ResponseEntity<UserSummary> verifyEmail(@PathVariable Long userId) {
    authorizationUtil.requireAdmin();

    log.info("Admin이 사용자 이메일 강제 인증: userId={}", userId);

    User updatedUser = userService.adminVerifyEmail(userId);

    UserSummary summary = UserSummary.builder()
      .userId(updatedUser.getId())
      .email(updatedUser.getEmail())
      .username(updatedUser.getNickname())
      .role(updatedUser.getRole())
      .enabled(updatedUser.isEnabled())
      .emailVerified(updatedUser.isEmailVerified())
      .createdAt(updatedUser.getCreatedAt())
      .lastLoginAt(updatedUser.getLastLoginAt())
      .build();

    return ResponseEntity.ok(summary);
  }

  /**
   * 강제 탈퇴
   */
  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
    authorizationUtil.requireAdmin();

    log.info("Admin이 사용자 강제 탈퇴: userId={}", userId);

    userService.adminDeleteUser(userId);

    return ResponseEntity.ok().build();
  }

  // ==================== DTO ====================

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserSummary {

    private Long userId;
    private String email;
    private String username;
    private UserRole role;
    private boolean enabled;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserDetail {

    private Long userId;
    private String email;
    private String username;
    private UserRole role;
    private boolean enabled;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private List<AccountSummary> accounts;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AccountSummary {

    private Long accountId;
    private String accountName;
    private BigDecimal balanceKrw;
    private BigDecimal balanceUsd;
    private LocalDateTime createdAt;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdateRoleRequest {

    private UserRole role;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdateEnabledRequest {

    private boolean enabled;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdateNicknameRequest {

    private String nickname;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdatePasswordRequest {

    private String newPassword;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserStats {

    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long adminUsers;
  }
}
