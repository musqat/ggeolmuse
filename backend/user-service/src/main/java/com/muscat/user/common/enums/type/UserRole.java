package com.muscat.user.common.enums.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 권한 유형
 */
@Getter
@RequiredArgsConstructor
public enum UserRole {

    USER("일반 사용자"),
    ADMIN("관리자");

    private final String description;
}
