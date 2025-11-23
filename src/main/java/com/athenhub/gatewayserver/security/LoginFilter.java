package com.athenhub.gatewayserver.security;

import com.athenhub.gatewayserver.error.GatewayAuthenticationException;
import com.athenhub.gatewayserver.error.GlobalErrorCode;
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
 * <p>인증된 사용자의 JWT에서 사용자 정보를 읽어, 각 서비스로 전달할 헤더를 추가한다.
 *
 * <ul>
 *   <li>subject → {@code X-User-Id}
 *   <li>preferred_username → {@code X-Username}
 *   <li>given_name + family_name → {@code X-User-Name}(URL 인코딩)
 *   <li>roles → {@code X-User-Roles}
 *   <li>slack_id / slackId → {@code X-Slack-Id}
 * </ul>
 *
 * <p>Slack ID가 존재하지 않으면 {@link GatewayAuthenticationException}을 발생시키며, 전역 예외 핸들러에서 공통 에러 포맷(JSON)으로
 * 응답한다.
 */
@Component
public class LoginFilter implements GlobalFilter, Ordered {
  // GlobalFilter, Ordered 이 두개의 인터페이스를 구현한 LoginFilter 클래스

  // 사용자 정보를 담기 위한 HTTP 헤더 이름 상수들
  private static final String HEADER_USER_ID = "X-User-Id";
  private static final String HEADER_USERNAME = "X-Username";
  private static final String HEADER_USER_NAME = "X-User-Name";
  private static final String HEADER_SLACK_ID = "X-Slack-Id";
  private static final String HEADER_ROLES = "X-User-Roles";

  // 가장 높은 우선순위로 필터를 실행하도록 설정
  // (숫자가 작을수록 먼저 실행되며, HIGHEST_PRECEDENCE는 최우선 실행)
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

    // JWT에 Claim 값으로 roles가 있는지 체크
    if (jwt.hasClaim("roles")) {
      Object v = jwt.getClaim("roles");
      if (v instanceof Collection<?> collection) {
        collection.forEach(r -> result.add(r.toString()));
      }
      // JWT에 roles는 없고 realm_access는 있는지 체크
    } else if (jwt.hasClaim("realm_access")) {
      Map<String, Object> realmAccess = jwt.getClaim("realm_access");
      Object r = realmAccess.get("roles");
      if (r instanceof Collection<?> collection) {
        collection.forEach(role -> result.add(role.toString()));
      }
      // JWT에 roles도 없고 realm_access도 없고 resource_access가 있는지 체크
    } else if (jwt.hasClaim("resource_access")) {
      Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
      resourceAccess
          .values()
          .forEach(
              v -> {
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
    // Gateway를 지나가는 모든 요청이 꼭 거쳐 가는 필터의 메인 메서드

    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> ctx == null ? null : ctx.getAuthentication())
        .flatMap(
            auth -> {
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
              String name =
                  URLEncoder.encode(
                      Objects.toString(givenName, "") + Objects.toString(familyName, ""),
                      StandardCharsets.UTF_8);

              // Slack ID: 반드시 존재해야 함 (없으면 예외 발생 → 전역 핸들러에서 처리)
              String slackId = jwt.getClaimAsString("slack_id");
              if (!StringUtils.hasText(slackId)) {
                slackId = jwt.getClaimAsString("slackId");
              }

              if (!StringUtils.hasText(slackId)) {
                return Mono.error(
                    new GatewayAuthenticationException(
                        GlobalErrorCode.UNAUTHORIZED, "Slack ID가 JWT에 존재하지 않습니다."));
              }

              // 헤더에 사용자 정보 추가
              ServerHttpRequest mutatedRequest =
                  exchange
                      .getRequest()
                      .mutate()
                      .header(HEADER_USER_ID, Objects.toString(jwt.getSubject(), ""))
                      .header(
                          HEADER_USERNAME,
                          Objects.toString(jwt.getClaimAsString("preferred_username"), ""))
                      .header(
                          HEADER_ROLES,
                          roles.stream()
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
