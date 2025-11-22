package com.athenhub.gatewayserver.config;

import com.athenhub.commoncore.error.GlobalErrorCode;
import com.athenhub.gatewayserver.error.GatewayAuthenticationException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 전역 필터.
 *
 * <p>인증된 사용자의 JWT에서 사용자 정보를 읽어, 각 서비스로 전달할 헤더를 추가한다.</p>
 *
 * <ul>
 *   <li>subject → {@code X-User-Id}</li>
 *   <li>preferred_username → {@code X-Username}</li>
 *   <li>given_name + family_name → {@code X-User-Name}(URL 인코딩)</li>
 *   <li>roles → {@code X-User-Roles}</li>
 *   <li>slack_id / slackId → {@code X-Slack-Id}</li>
 * </ul>
 *
 * <p>Slack ID가 존재하지 않으면 {@link GatewayAuthenticationException}을 발생시키며,
 * 전역 예외 핸들러에서 공통 에러 포맷(JSON)으로 응답한다.</p>
 */
@Component
public class LoginFilter implements GlobalFilter, Ordered {

  private static final String HEADER_USER_ID = "X-User-Id";
  private static final String HEADER_USERNAME = "X-Username";
  private static final String HEADER_USER_NAME = "X-User-Name";
  private static final String HEADER_SLACK_ID = "X-Slack-Id";
  private static final String HEADER_ROLES = "X-User-Roles";

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  /**
   * JWT에서 권한(roles) 목록을 추출한다.
   *
   * @param jwt 권한 정보를 포함하는 JWT 토큰
   * @return 문자열 형태의 역할 이름 리스트
   */
  private List<String> extractRoles(Jwt jwt) {
    List<String> result = new ArrayList<>();

    if (jwt.hasClaim("roles")) {
      Object v = jwt.getClaim("roles");
      if (v instanceof Collection<?> collection) {
        collection.forEach(r -> result.add(r.toString()));
      }
    } else if (jwt.hasClaim("realm_access")) {
      Map<String, Object> realmAccess = jwt.getClaim("realm_access");
      Object r = realmAccess.get("roles");
      if (r instanceof Collection<?> collection) {
        collection.forEach(role -> result.add(role.toString()));
      }
    } else if (jwt.hasClaim("resource_access")) {
      Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
      resourceAccess.values().forEach(v -> {
        if (v instanceof Map<?, ?> map) {
          Object rr = map.get("roles");
          if (rr instanceof Collection<?> roles) {
            roles.forEach(role -> result.add(role.toString()));
          }
        }
      });
    }
    return result;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> ctx == null ? null : ctx.getAuthentication())
        .flatMap(auth -> {
          if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            // 인증 정보가 없거나 JWT 기반이 아니면 그대로 진행
            return chain.filter(exchange);
          }

          Jwt jwt = jwtAuth.getToken();

          // 권한 정보
          List<String> roles = extractRoles(jwt);

          // full name (given_name + family_name)
          String givenName = jwt.getClaimAsString("given_name");
          String familyName = jwt.getClaimAsString("family_name");
          String name = URLEncoder.encode(
              Objects.toString(givenName, "") + Objects.toString(familyName, ""),
              StandardCharsets.UTF_8
          );

          // Slack ID: 반드시 존재해야 함 (없으면 예외 발생 → 전역 핸들러에서 처리)
          String slackId = jwt.getClaimAsString("slack_id");
          if (!StringUtils.hasText(slackId)) {
            slackId = jwt.getClaimAsString("slackId");
          }

          if (!StringUtils.hasText(slackId)) {
            return Mono.error(new GatewayAuthenticationException(
                GlobalErrorCode.UNAUTHORIZED,
                "Slack ID가 JWT에 존재하지 않습니다."
            ));
          }

          // 헤더에 사용자 정보 추가
          ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
              .header(HEADER_USER_ID, Objects.toString(jwt.getSubject(), ""))
              .header(HEADER_USERNAME, Objects.toString(jwt.getClaimAsString("preferred_username"), ""))
              .header(HEADER_ROLES, roles.stream()
                  .filter(s -> s.startsWith("ROLE_"))
                  .collect(Collectors.joining(",")))
              .header(HEADER_USER_NAME, name)
              .header(HEADER_SLACK_ID, slackId)
              .build();

          return chain.filter(exchange.mutate().request(mutatedRequest).build());
        })
        // SecurityContext 자체가 비어 있는 경우 기본 체인 진행
        .switchIfEmpty(chain.filter(exchange));
  }
}
