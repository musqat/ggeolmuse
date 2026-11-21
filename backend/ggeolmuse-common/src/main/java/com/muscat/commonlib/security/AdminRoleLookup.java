package com.muscat.commonlib.security;

// 사용자 Keycloak ID 가 활성 관리자인지 묻는 쪽
@FunctionalInterface
public interface AdminRoleLookup {

  boolean isActiveAdmin(String keycloakId);
}
