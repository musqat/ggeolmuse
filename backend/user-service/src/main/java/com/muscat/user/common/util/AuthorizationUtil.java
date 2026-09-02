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

/**
 * 권한 체크 유틸리티
 * JWT 토큰에서 keycloakId를 추출하고, DB에서 사용자 role을 확인합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationUtil {

    private final UserRepository userRepository;

    /**
     * 현재 인증된 사용자의 keycloakId 추출
     *
     * @return keycloakId (JWT의 sub claim)
     */
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

    /**
     * 현재 인증된 사용자 조회
     *
     * @return User 엔티티
     */
    public User getCurrentUser() {
        String keycloakId = getCurrentKeycloakId();
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> {
                    log.warn("DB에 사용자 없음: keycloakId={}", keycloakId);
                    return new UserException(UserResponse.USER_NOT_FOUND);
                });
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

    /**
     * 특정 사용자가 본인 또는 ADMIN인지 확인
     */
    public void requireSelfOrAdmin(Long targetUserId) {
        User currentUser = getCurrentUser();

        if (currentUser.getId().equals(targetUserId)) {
            log.debug("본인 접근: userId={}", currentUser.getId());
            return;
        }

        if (currentUser.getRole() == UserRole.ADMIN) {
            log.debug("ADMIN 접근: adminId={}, targetUserId={}", currentUser.getId(), targetUserId);
            return;
        }

        log.warn("권한 없음: userId={}, targetUserId={}, role={}",
                currentUser.getId(), targetUserId, currentUser.getRole());
        throw new UserException(UserResponse.FORBIDDEN);
    }
}
