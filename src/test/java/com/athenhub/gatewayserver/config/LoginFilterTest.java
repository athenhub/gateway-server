package com.athenhub.gatewayserver.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.athenhub.gatewayserver.error.GatewayAuthenticationException;
import com.athenhub.gatewayserver.error.GlobalErrorCode;
import com.athenhub.gatewayserver.security.LoginFilter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

class LoginFilterTest {

  private LoginFilter loginFilter;
  private Jwt jwtWithSlackId; // slack_id 있음
  private Jwt jwtWithoutSlackId; // slack_id 없음

  @BeforeEach
  void setUp() {
    loginFilter = new LoginFilter();

    // 기본 JWT: slack_id 있고, ROLE_만 있는 단순한 형태
    jwtWithSlackId =
        createJwt(true, List.of("ROLE_MASTER_MANAGER", "ROLE_HUB_MANAGER"), "slack_id", "U123456");

    jwtWithoutSlackId =
        createJwt(false, List.of("ROLE_MASTER_MANAGER", "ROLE_HUB_MANAGER"), null, null);
  }

  private Jwt createJwt(
      boolean includeSlackId,
      List<String> roles,
      String slackIdKey, // "slack_id" or "slackId" or null
      String slackIdValue) {
    Instant now = Instant.now();
    Map<String, Object> headers = Map.of("alg", "none");

    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "user-1234");
    claims.put("preferred_username", "johndoe");
    claims.put("given_name", "길동");
    claims.put("family_name", "홍");

    if (roles != null) {
      claims.put("roles", roles);
    }

    if (includeSlackId && slackIdKey != null) {
      claims.put(slackIdKey, slackIdValue);
    }

    return new Jwt("dummy-token", now, now.plusSeconds(3600), headers, claims);
  }

  @Test
  @DisplayName("roles 중 ROLE_로 시작하는 값만 X-User-Roles 헤더에 포함한다")
  void onlyRolePrefixValuesAreAdded() {
    // given
    Instant now = Instant.now();
    Map<String, Object> headers = Map.of("alg", "none");

    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "user-1234");
    claims.put("preferred_username", "johndoe");
    claims.put("given_name", "길동");
    claims.put("family_name", "홍");
    claims.put("roles", List.of("ROLE_MASTER_MANAGER", "read", "ROLE_HUB_MANAGER", "write"));
    claims.put("slack_id", "U123456");

    Jwt jwt = new Jwt("dummy-token", now, now.plusSeconds(3600), headers, claims);
    Authentication auth = new JwtAuthenticationToken(jwt);

    MockServerHttpRequest request = MockServerHttpRequest.get("/v1/hubs").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
    GatewayFilterChain chain =
        ex -> {
          capturedRequest.set(ex.getRequest());
          return Mono.empty();
        };

    // when
    loginFilter
        .filter(exchange, chain)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        .block();

    // then
    var h = capturedRequest.get().getHeaders();
    assertThat(h.getFirst("X-User-Roles")).isEqualTo("ROLE_MASTER_MANAGER,ROLE_HUB_MANAGER");
  }

  @Test
  @DisplayName("Slack Id가 있으면 X-User-* 헤더를 추가하고 체인을 계속 진행한다")
  void addUserHeaders_whenSlackIdPresent() {
    // given
    Authentication auth = new JwtAuthenticationToken(jwtWithSlackId);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/v1/hubs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer dummy-token")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();

    GatewayFilterChain chain =
        ex -> {
          capturedRequest.set(ex.getRequest());
          return Mono.empty();
        };

    // when
    loginFilter
        .filter(exchange, chain)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        .block();

    // then
    assertThat(capturedRequest.get()).as("체인이 호출되어야 한다").isNotNull();

    var headers = capturedRequest.get().getHeaders();
    assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1234");
    assertThat(headers.getFirst("X-Username")).isEqualTo("johndoe");
    assertThat(headers.getFirst("X-User-Roles")).isEqualTo("ROLE_MASTER_MANAGER,ROLE_HUB_MANAGER");
    assertThat(headers.getFirst("X-Slack-Id")).isEqualTo("U123456");
    assertThat(headers.getFirst("X-User-Name")).isNotBlank();

    // 필터에서는 상태코드를 건드리지 않는다 (전역 예외 핸들러가 담당)
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  @DisplayName("Slack Id가 없으면 GatewayAuthenticationException을 던진다")
  void throwException_whenSlackIdMissing() {
    // given
    Authentication auth = new JwtAuthenticationToken(jwtWithoutSlackId);

    var request =
        MockServerHttpRequest.get("/v1/hubs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer dummy-token")
            .build();
    var exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain = ex -> Mono.empty();

    // when
    Throwable throwable =
        catchThrowable(
            () ->
                loginFilter
                    .filter(exchange, chain)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                    .block());

    // then
    assertThat(throwable).isInstanceOf(GatewayAuthenticationException.class);

    GatewayAuthenticationException ex = (GatewayAuthenticationException) throwable;
    assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.UNAUTHORIZED);
    assertThat(ex.getMessage()).isEqualTo("Slack ID가 JWT에 존재하지 않습니다.");
  }
}
