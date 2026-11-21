package com.muscat.marketdata.infra.client;

import com.muscat.marketdata.infra.client.dto.AdminRoleDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", url = "http://user-service:8080")
public interface UserServiceClient {

  // 받은 Keycloak ID 사용자가 활성 관리자인지(서비스 토큰)
  @GetMapping("/api/internal/users/admin-role")
  AdminRoleDto getAdminRole(@RequestParam("keycloakId") String keycloakId);
}
