package com.athenhub.gatewayserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakClientRoleConverterTest {

  private final KeycloakClientRoleConverter converter = new KeycloakClientRoleConverter();

  private Jwt createJwtWithClaims(Map<String, Object> claims) {
    Instant now = Instant.now();
    Map<String, Object> headers = Map.of("alg", "none");

    return new Jwt(
        "dummy-token",
        now,
        now.plusSeconds(3600),
        headers,
        claims
    );
  }

  @Test
  @DisplayName("realm_access.roles 에 있는 역할을 ROLE_ prefix 를 붙여서 권한으로 변환한다")
  void convert_realmAccessRoles() {
    // given
    Map<String, Object> claims = Map.of(
        "realm_access", Map.of(
            "roles", List.of("master_admin", "HUB_MANAGER")
        )
    );
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities =
        converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_master_admin",
            "ROLE_HUB_MANAGER"
        );
  }

  @Test
  @DisplayName("realm_access 가 없으면 resource_access.*.roles 에서 역할을 읽어온다")
  void convert_resourceAccessRoles() {
    // given
    Map<String, Object> claims = Map.of(
        "resource_access", Map.of(
            "client-a", Map.of("roles", List.of("vendor", "shipper")),
            "client-b", Map.of("roles", List.of("reader"))
        )
    );
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities =
        converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_vendor",
            "ROLE_shipper",
            "ROLE_reader"
        );
  }

  @Test
  @DisplayName("이미 ROLE_ prefix 가 붙어있는 역할은 중복으로 붙이지 않는다")
  void convert_roleAlreadyPrefixed() {
    // given
    Map<String, Object> claims = Map.of(
        "realm_access", Map.of(
            "roles", List.of("ROLE_ADMIN", "USER")
        )
    );
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities =
        converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_ADMIN",   // 그대로 유지
            "ROLE_USER"     // 새로 prefix 추가
        );
  }

  @Test
  @DisplayName("roles 정보가 없으면 빈 권한 목록을 반환한다")
  void convert_noRoles() {
    // given
    Map<String, Object> claims = Map.of("realm_access", Map.of("roles", List.of()));
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities =
        converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities).isEmpty();
  }
}
