package com.muscat.commonlib.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

// Keycloak JWT를 Spring Security 권한으로 변환 (Non-Reactive 서비스용)
public class KeycloakJwtAuthenticationConverter
  implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    return new JwtAuthenticationToken(jwt, authorities);
  }

  // JWT의 realm_access.roles에서 권한 목록 추출
  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null) {
      return Collections.emptyList();
    }

    Object rolesObj = realmAccess.get("roles");
    if (!(rolesObj instanceof List)) {
      return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) rolesObj;

    return roles.stream()
      .map(SimpleGrantedAuthority::new)
      .collect(Collectors.toList());
  }
}
