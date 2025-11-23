package com.athenhub.gatewayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring WebFlux 기반 보안 설정 클래스.
 *
 * <p>이 Gateway 애플리케이션을 OAuth2 Resource Server(JWT)로 동작시키고, 공개 엔드포인트와 인증이 필요한 엔드포인트를 구분하는 역할을 한다.
 *
 * <ul>
 *   <li>헬스 체크 및 모니터링, Swagger UI 등은 인증 없이 접근 허용
 *   <li>그 외 모든 API 요청은 JWT 인증을 요구
 *   <li>Keycloak에서 발급한 JWT의 역할 정보를 {@code GrantedAuthority}로 매핑
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  /**
   * Gateway 서버에 적용할 {@link SecurityWebFilterChain}을 구성한다.
   *
   * <p>설정 내용은 다음과 같다.
   *
   * <ul>
   *   <li>CSRF 비활성화 (REST API + 토큰 기반 인증이므로 불필요)
   *   <li>{@code /actuator/**}, {@code /swagger-ui/**} 등은 인증 없이 접근 허용
   *   <li>그 외 모든 요청은 인증(유효한 JWT 토큰)을 요구
   *   <li>JWT → 인증 객체 변환 시 {@link KeycloakClientRoleConverter}를 사용하여 Keycloak 역할을 Spring Security
   *       권한으로 매핑
   * </ul>
   *
   * @param http Spring WebFlux 보안 구성을 위한 {@link ServerHttpSecurity}
   * @return 구성 완료된 {@link SecurityWebFilterChain}
   */
  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    // JWT에서 roles 정보를 꺼내어 GrantedAuthority로 변환하는 Converter 설정
    ReactiveJwtAuthenticationConverter conv = new ReactiveJwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(new KeycloakClientRoleConverter());

    return http
        // REST API + JWT 환경이므로 CSRF는 사용하지 않음
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        // 요청 경로별 인가(authorization) 규칙 정의
        .authorizeExchange(
            exchanges ->
                exchanges
                    // 헬스 체크, 모니터링, 문서 등 공개 엔드포인트
                    .pathMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()

                    // 그 외 모든 요청은 JWT 인증 필요
                    // (세부 권한/역할 검증은 각 도메인 서비스에서 수행)
                    .anyExchange()
                    .authenticated())
        // 이 Gateway를 OAuth2 Resource Server(JWT 기반)로 동작시키는 설정
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(conv)))
        .build();
  }
}
