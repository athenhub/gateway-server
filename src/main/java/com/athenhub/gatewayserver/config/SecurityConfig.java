package com.athenhub.gatewayserver.config;

import static org.springframework.http.HttpMethod.OPTIONS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    ReactiveJwtAuthenticationConverter conv = new ReactiveJwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(new KeycloakClientRoleConverter());

    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchanges -> exchanges
            // 1. 헬스 체크, 모니터링, 문서 등 공개 엔드포인트
            .pathMatchers(
                "/actuator/health",
                "/actuator/info",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).permitAll()

            // 2. CORS preflight 요청
            .pathMatchers(OPTIONS, "/**").permitAll()

            // 3. 그 외 모든 요청은 JWT가 있어야 함 (역할 체크는 각 서비스에서)
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(conv)))
        .build();
  }
}
