package com.muscat.user.config;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

// 시작 시 초기 데이터 로드: Admin 계정을 만들어 Keycloak realm 사용자와 맞춤
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialDataLoader implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeycloakService keycloakService;

    @Value("${app.admin.email:admin@muscathan.com}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:#{null}}")
    private String adminPassword;

    @Value("${app.admin.nickname:Admin}")
    private String adminNickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=== 초기 데이터 로딩 시작 ===");
        createAdminUserIfNotExists();
        log.info("=== 초기 데이터 로딩 완료 ===");
    }

    private void createAdminUserIfNotExists() {
        Optional<User> existingUser = userRepository.findByEmail(adminEmail);

        if (existingUser.isPresent()) {
            log.info("Admin 사용자가 이미 존재합니다: {}", adminEmail);
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin 비밀번호가 설정되지 않았습니다. Admin 계정 생성을 스킵합니다.");
            log.warn("환경 변수 ADMIN_PASSWORD를 설정하세요.");
            return;
        }

        log.info("Admin 사용자 생성 중: {}", adminEmail);

        try {
            // 1. Keycloak 사용자 이름은 처음 email 그대로라, Keycloak 에서 email 을 바꾼 관리자도 같은 계정으로 찾음
            String keycloakId = keycloakService.findUserIdByUsername(adminEmail);
            if (keycloakId != null && userRepository.findByKeycloakId(keycloakId).isPresent()) {
                log.info("Admin 사용자가 이미 존재합니다: keycloakId={}", keycloakId);
                return;
            }
            if (keycloakId == null) {
                keycloakId = keycloakService.createAdminUser(adminEmail, adminPassword);
                log.info("Keycloak에 Admin 사용자 생성 완료: keycloakId={}", keycloakId);
            } else {
                keycloakService.assignRealmRole(keycloakId, "admin");
                log.info("Keycloak 기존 Admin 사용자에게 admin role 할당 완료: keycloakId={}", keycloakId);
            }

            // 2. user-service DB에 Admin 사용자 저장
            User adminUser = User.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .nickname(adminNickname)
                    .keycloakId(keycloakId)
                    .provider(AuthType.LOCAL)
                    .role(UserRole.ADMIN)
                    .emailVerified(true)
                    .enabled(true)
                    .build();

            User savedUser = userRepository.save(adminUser);
            log.info("user-service DB에 Admin 사용자 생성 완료: id={}, email={}, keycloakId={}",
                    savedUser.getId(), savedUser.getEmail(), savedUser.getKeycloakId());

            // 3. 기본 계좌 생성
            Account account = Account.builder()
                    .user(savedUser)
                    .accountNumber(generateAccountNumber(savedUser.getId()))
                    .accountName("Admin 기본 계좌")
                    .balanceKrw(BigDecimal.ZERO)
                    .balanceUsd(BigDecimal.ZERO)
                    .build();

            accountRepository.save(account);
            log.info("Admin 기본 계좌 생성 완료: accountNumber={}", account.getAccountNumber());

        } catch (Exception e) {
            log.error("Admin 사용자 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Admin 사용자 생성 실패", e);
        }
    }

    private String generateAccountNumber(Long userId) {
        // 간단한 계좌번호 생성: USER-{userId}-{timestamp}
        return String.format("ADMIN-%d-%d", userId, System.currentTimeMillis() % 100000);
    }
}
