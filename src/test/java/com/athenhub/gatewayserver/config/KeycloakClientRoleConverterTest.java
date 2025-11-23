package com.athenhub.gatewayserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.athenhub.gatewayserver.security.KeycloakClientRoleConverter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 테스트 종합 정리 1. realm_access.roles 읽어서 ROLE_ 붙여서 Spring 권한으로 변환 되는지. 2. realm_access 없을 때
 * resource_access.*roles에서 역할을 다 모아서 변환 되는지. 3. realm_access, resource_access 둘다 있을 때 realm_access만
 * 사용되고 resource_access는 무시 되는지. 4. 이미 ROLE_가 붙어 있는 건 그대로 두고, 없는 것만 ROLE_ 붙이는지. 5. roles가 비어 있으면 결과가
 * 빈 컬렉션(Flux.empty())이 되는지.
 */
class KeycloakClientRoleConverterTest {

  private final KeycloakClientRoleConverter converter = new KeycloakClientRoleConverter();

  // 더미 토큰값 미리 생성해주는 헬퍼 메서드
  private Jwt createJwtWithClaims(Map<String, Object> claims) {
    Instant now = Instant.now();
    Map<String, Object> headers = Map.of("alg", "none");

    return new Jwt("dummy-token", now, now.plusSeconds(3600), headers, claims);
  }

  @Test
  @DisplayName("realm_access.roles에 있는 역할을 ROLE_를 붙여서 Spring 권한으로 변환한다")
  void convert_realmAccessRoles() {
    // given
    Map<String, Object> claims =
        Map.of("realm_access", Map.of("roles", List.of("master_admin", "HUB_MANAGER")));
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities = converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_master_admin", "ROLE_HUB_MANAGER");
  }

  @Test
  @DisplayName("realm_access 가 없으면 resource_access.*.roles 에서 역할을 읽어온다")
  void convert_resourceAccessRoles() {
    // given
    Map<String, Object> claims =
        Map.of(
            "resource_access",
            Map.of(
                "client-a", Map.of("roles", List.of("VENDOR_AGENT", "SHIPPING_AGENT")),
                "client-b", Map.of("roles", List.of("MASTER_MANAGER"))));
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities = converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_VENDOR_AGENT", "ROLE_SHIPPING_AGENT", "ROLE_MASTER_MANAGER");
  }

  @Test
  @DisplayName("realm_access 와 resource_access 가 동시에 있으면 realm_access 만 사용한다")
  void convert_realmAccessHasPriorityOverResourceAccess() {
    // given
    Map<String, Object> claims =
        Map.of(
            "realm_access", Map.of("roles", List.of("realm_role")),
            "resource_access", Map.of("client", Map.of("roles", List.of("client_role"))));
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities = converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_realm_role")
        // resource_access 쪽 역할은 포함되면 안 됨
        .doesNotContain("ROLE_client_role");
  }

  @Test
  @DisplayName("이미 ROLE_ prefix 가 붙어있는 역할은 중복으로 붙이지 않는다")
  void convert_roleAlreadyPrefixed() {
    // given
    Map<String, Object> claims =
        Map.of("realm_access", Map.of("roles", List.of("ROLE_MASTER_MANAGER", "SHIPPING_AGENT")));
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities = converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_MASTER_MANAGER", // 그대로 유지
            "ROLE_SHIPPING_AGENT" // 새로 prefix 추가
        );
  }

  @Test
  @DisplayName("roles 정보가 없으면 빈 권한 목록을 반환한다")
  void convert_noRoles() {
    // given
    Map<String, Object> claims = Map.of("realm_access", Map.of("roles", List.of()));
    Jwt jwt = createJwtWithClaims(claims);

    // when
    Collection<GrantedAuthority> authorities = converter.convert(jwt).collectList().block();

    // then
    assertThat(authorities).isEmpty();
  }
}
