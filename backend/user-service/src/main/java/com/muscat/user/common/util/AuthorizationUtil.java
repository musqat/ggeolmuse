package com.muscat.user.common.util;

import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// 권한 체크 유틸: JWT 의 keycloakId 로 DB 사용자 역할을 확인
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationUtil {

    private final UserRepository userRepository;

    // JWT 의 sub
    public String getCurrentKeycloakId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("인증되지 않은 요청");
            throw new UserException(UserResponse.USER_NOT_FOUND);
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        log.warn("JWT 토큰이 아닌 인증 정보: {}", authentication.getPrincipal().getClass());
        throw new UserException(UserResponse.USER_NOT_FOUND);
    }

    public User getCurrentUser() {
        String keycloakId = getCurrentKeycloakId();
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> {
                    log.warn("DB에 사용자 없음: keycloakId={}", keycloakId);
                    return new UserException(UserResponse.USER_NOT_FOUND);
                });

        if (!user.isEnabled()) {
            log.warn("비활성 사용자 요청: keycloakId={}", keycloakId);
            throw new UserException(UserResponse.ACCOUNT_DISABLED);
        }
        return user;
    }

    /**
     * 현재 사용자가 ADMIN 권한을 가지고 있는지 확인
     */
    public void requireAdmin() {
        User user = getCurrentUser();

        if (user.getRole() != UserRole.ADMIN) {
            log.warn("ADMIN 권한 없음: userId={}, role={}", user.getId(), user.getRole());
            throw new UserException(UserResponse.FORBIDDEN);
        }

        log.debug("ADMIN 권한 확인: userId={}, email={}", user.getId(), user.getEmail());
    }

    /**
     * 현재 사용자가 ADMIN 권한을 가지고 있는지 확인 (boolean 반환)
     */
    public boolean isAdmin() {
        try {
            User user = getCurrentUser();
            return user.getRole() == UserRole.ADMIN;
        } catch (Exception e) {
            log.debug("ADMIN 권한 체크 실패: {}", e.getMessage());
            return false;
        }
    }
}
