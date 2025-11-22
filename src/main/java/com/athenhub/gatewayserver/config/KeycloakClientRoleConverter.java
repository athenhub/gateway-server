package com.athenhub.gatewayserver.config;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;


public class KeycloakClientRoleConverter implements Converter<Jwt, Flux<GrantedAuthority>> {

  @Override
  public Flux<GrantedAuthority> convert(Jwt jwt) {
    Object realmAccessObj = jwt.getClaims().get("realm_access");
    if (realmAccessObj instanceof Map) {
      Object rolesObj = ((Map<?, ?>) realmAccessObj).get("roles");
      if (rolesObj instanceof Collection) {
        return Flux.fromIterable((Collection<?>) rolesObj)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(this::normalizeRole)
            .map(SimpleGrantedAuthority::new);
      }
    }

    // 2) resource_access.{client}.roles 처리 (client-level 역할)
    Object resourceAccessObj = jwt.getClaims().get("resource_access");
    if (resourceAccessObj instanceof Map) {
      Map<?, ?> resourceAccess = (Map<?, ?>) resourceAccessObj;
      // 모든 클라이언트의 roles 합치기
      return Flux.fromIterable(resourceAccess.values())
          .filter(v -> v instanceof Map)
          .flatMap(mapObj -> {
            Object roles = ((Map<?, ?>) mapObj).get("roles");
            if (roles instanceof Collection) {
              return Flux.fromIterable((Collection<?>) roles);
            }
            return Flux.empty();
          })
          .filter(Objects::nonNull)
          .map(Object::toString)
          .map(this::normalizeRole)
          .map(SimpleGrantedAuthority::new);
    }

    return Flux.empty();
  }

  private String normalizeRole(String role) {
    if (role == null) return "";
    String trimmed = role.trim();
    return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
  }
}