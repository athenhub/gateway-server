package com.athenhub.gatewayserver.security;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;

/**
 * Keycloak JWT 안에 들어 있는 역할(role) 정보를 Spring Security에서 사용하는 {@link GrantedAuthority} 형태로 변환하는
 * Converter.
 *
 * <p>이 클래스의 역할
 *
 * <ul>
 *   <li>Keycloak이 발급한 JWT에서 realm-level / client-level 역할을 읽어온다.
 *   <li>읽어온 역할 문자열들을 {@code ROLE_} prefix를 가진 형식으로 표준화한다.
 *   <li>각 역할을 {@link SimpleGrantedAuthority}로 감싸서 {@link Flux}로 반환한다.
 * </ul>
 *
 * <p>Keycloak JWT 구조 예시
 *
 * <pre>
 * {
 *   "realm_access": {
 *     "roles": ["admin", "user"]
 *   },
 *   "resource_access": {
 *     "gateway-server": {
 *       "roles": ["gateway_admin", "gateway_user"]
 *     },
 *     "some-other-client": {
 *       "roles": ["client_role1"]
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>변환 규칙
 *
 * <ul>
 *   <li>{@code realm_access.roles}가 존재하면 그 역할들을 우선적으로 사용한다.
 *   <li>realm-level 역할이 없으면 {@code resource_access.{client}.roles}를 모두 모아서 사용한다.
 *   <li>역할 문자열 앞에 {@code ROLE_} prefix가 없으면 자동으로 붙인다. (예: {@code "admin" → "ROLE_admin"}, {@code
 *       "ROLE_USER" → "ROLE_USER"})
 *   <li>null 이거나 비어 있는 역할 컬렉션이면 빈 {@link Flux}를 반환한다.
 * </ul>
 *
 * <p>사용 위치 예시
 *
 * <ul>
 *   <li>Spring Security 설정에서 {@code JwtAuthenticationConverter}에 주입해서 사용한다.
 *   <li>Keycloak의 역할 정보를 Spring Security의 권한 체크 ({@code hasRole("admin")}, {@code
 *       hasAuthority("ROLE_admin")})와 연결할 때 사용한다.
 * </ul>
 */
public class KeycloakClientRoleConverter implements Converter<Jwt, Flux<GrantedAuthority>> {
  // Jwt를 받아서 Flux<GrantedAuthority>로 바꿔주는 클래스.
  // 즉 Jwt를 Flux<GrantedAuthority>로 변환하는 Conver 인터페이스의 구현체.

  /**
   * Keycloak이 발급한 {@link Jwt} 토큰에서 역할(role)을 추출해 {@link GrantedAuthority} 스트림으로 변환한다.
   *
   * <p>처리 순서
   *
   * <ol>
   *   <li>{@code realm_access.roles}를 찾아서 있으면 그것만 사용한다.
   *   <li>없으면 {@code resource_access.{client}.roles}들을 모두 모아서 사용한다.
   *   <li>각 역할 문자열에 대해 {@link #normalizeRole(String)}로 {@code ROLE_} prefix를 보정한다.
   *   <li>보정된 문자열을 {@link SimpleGrantedAuthority}로 감싼 뒤 {@link Flux}로 반환한다.
   * </ol>
   *
   * @param jwt Keycloak이 발급한 인증용 JWT 토큰
   * @return JWT에 담긴 역할 정보를 기반으로 생성된 {@link GrantedAuthority} 스트림. 역할이 없다면 빈 {@link Flux}를 반환한다.
   */
  @Override
  public Flux<GrantedAuthority> convert(Jwt jwt) {
    // 변환 메스드

    // JWT 페이로드에서 realm_access 클레임을 꺼내서 Object 타입 변수에 담는 코드
    // 어떤값이 올지 몰라서 Object 객체 사용
    Object realmAccessObj = jwt.getClaims().get("realm_access");
    // realmAccessObj가 진짜 Map 타입인지 확인하는 조건문
    if (realmAccessObj instanceof Map) {
      // realmAccessObj를 Map으로 캐스팅, 그 안에 "roles"키에 해당하는 값 꺼내기
      Object rolesObj = ((Map<?, ?>) realmAccessObj).get("roles");
      // roldesObj, 즉 "roles" 값이 리스트/컬렉션 타입인지 확인하는 조건문
      if (rolesObj instanceof Collection) {
        // reloesObj를 Collection으로 캐스팅, 리액티브 스트림인 Flux로 감싸기
        return Flux.fromIterable((Collection<?>) rolesObj)
            // 그 스트림에서 null값은 걸러내기
            .filter(Objects::nonNull)
            // 각 요소를 String으로 변환
            .map(Object::toString)
            // 방금 만든 문자열에 normalizeRole 메서드 적용.
            .map(this::normalizeRole)
            // "ROLE_MASTER_MANAGER" 같은 문자열을 SimpleGrantedAuthority 객체로 감싸서 return
            .map(SimpleGrantedAuthority::new);
      }
    }

    // JWT의 클레임에서 "resource_access"라는 이름의 값을 꺼내서 Object에 담는 코드
    Object resourceAccessObj = jwt.getClaims().get("resource_access");
    // resourceAccessObj가 Map 형태인지 확인하는 조건문
    if (resourceAccessObj instanceof Map) {
      // resourceAccessObj를 Map으로 캐스팅해서 resourceAccess에 담는 코드
      Map<?, ?> resourceAccess = (Map<?, ?>) resourceAccessObj;
      // 각 클라이언트별 정보들만 모은 컬렉션
      // 이 컬렉션을 Flux로 변환해서 스트림을 만든다.
      return Flux.fromIterable(resourceAccess.values())
          // 각 value가 진짜 Map인것만 남긴다.
          .filter(v -> v instanceof Map)
          .flatMap(
              mapObj -> {
                // 각 클라이언트 정보(mapObj)에서 "roles" 값을 꺼낸다.
                Object roles = ((Map<?, ?>) mapObj).get("roles");
                // 꺼낸 "roles" 가 Collection을 포함하고 있는지 조건문
                if (roles instanceof Collection) {
                  // 포함할 경우에만 그 "roles"들을 스트림으로 흘려 보낸다.
                  return Flux.fromIterable((Collection<?>) roles);
                }
                // 컬렉션에 포함되지 않을 경우 클라이언트의 "roles"는 없는것으로 처리
                return Flux.empty();
              })
          // "roles" 리스트 안에 null인 것들은 제거
          .filter(Objects::nonNull)
          // 각 역할 값을 문자열로 변환
          .map(Object::toString)
          // "ROLE_"를 붙여주고 공뱅 제거와 같은 정규화
          .map(this::normalizeRole)
          // "SimpleGrantedAuthority"로 감싸서 Spring Security의 권한 객체로 변환한걸 return
          .map(SimpleGrantedAuthority::new);
    }
    // realm_access, resource_access 둘다 역할 정보를 찾지 못하면 빈 Flux 반환
    return Flux.empty();
  }

  /**
   * Keycloak에서 온 role 문자열을 Spring Security 관례에 맞게 정규화한다.
   *
   * <p>정규화 규칙
   *
   * <ul>
   *   <li>null인 경우 빈 문자열 {@code ""} 반환
   *   <li>앞뒤 공백 제거
   *   <li>{@code ROLE_}로 시작하지 않으면 {@code ROLE_} prefix를 붙인다.
   * </ul>
   *
   * <p>예시
   *
   * <ul>
   *   <li>{@code "admin" → "ROLE_admin"}
   *   <li>{@code " ROLE_USER " → "ROLE_USER"}
   *   <li>{@code "ROLE_MANAGER" → "ROLE_MANAGER"}
   * </ul>
   *
   * @param role Keycloak JWT에서 읽어온 role 문자열
   * @return Spring Security 권한 체크에 바로 사용할 수 있는 정규화된 role 문자열
   */
  private String normalizeRole(String role) {
    // 문자열 정규화 메서드
    // role이 null일 경우 ""처럼 빈 문자열 return
    if (role == null) {
      return "";
    }
    // 앞뒤 공백을 제거한 문자열로 정리
    String trimmed = role.trim();
    // 이미 "ROLE_"로 시작하면 그대로 반환, 아닐 경우 "ROLE_"를 붙여서 반환
    return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
  }
}
