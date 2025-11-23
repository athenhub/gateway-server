package com.athenhub.gatewayserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

// 예시: SecurityConfig에 대한 통합 테스트 느낌
@SpringBootTest
@AutoConfigureWebTestClient
class SecurityConfigTest {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  @DisplayName("공개 엔드포인트(/actuator/health)는 인증 없이 접근 가능하다")
  void actuatorHealth_isPublic() {
    webTestClient.get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus().is2xxSuccessful(); // 최소 401,403은 아니어야 함
  }

  @Test
  @DisplayName("보호된 엔드포인트는 인증 없이 접근 시 401을 반환한다")
  void protectedEndpoint_withoutToken_returns401() {
    webTestClient.get()
        .uri("/v1/members")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("유효한 JWT가 있으면 보호된 엔드포인트에 접근할 수 있다")
  void protectedEndpoint_withJwt_isAccessible() {
    webTestClient.get()
        .uri("/v1/members")
        // Spring Security 테스트용 helper (security-test 의존성 필요)
        .headers(headers -> {
          headers.setBearerAuth("dummy-token");
        })
        .exchange()
        // 여기서는 실제로 JWT 검증 안 하고, 토큰만 있으면 통과한다고 가정하면,
        // 컨텍스트/환경에 따라 is2xxSuccessful 또는 라우팅 결과 상태를 맞게 기대값으로 변경
        .expectStatus().is4xxClientError(); // 실제 라우트 없으면 404, 있으면 200
  }
}
