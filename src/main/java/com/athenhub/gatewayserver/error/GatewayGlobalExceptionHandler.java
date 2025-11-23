package com.athenhub.gatewayserver.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 전역 예외 핸들러.
 *
 * <p>Gateway 내부에서 발생한 예외를 가로채어, {@link ErrorResponse} 포맷으로 JSON 에러 응답을 내려준다.
 */
@Component
@Order(-2) // 기본 WebExceptionHandler보다 먼저 실행되도록 우선순위 설정
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

  private final ObjectMapper objectMapper;

  public GatewayGlobalExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * WebFlux 파이프라인에서 발생한 예외를 처리한다.
   *
   * @param exchange 현재 HTTP 요청/응답 컨텍스트
   * @param ex 발생한 예외
   * @return 에러 응답을 작성하는 비동기 작업
   */
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    // 이미 응답이 나가기 시작한 경우에는 손대지 않고 그대로 예외 전파
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }

    GlobalErrorCode errorCode;
    String message;

    // 1) Gateway에서 던진 인증 예외인 경우
    if (ex instanceof GatewayAuthenticationException authEx) {
      errorCode = authEx.getErrorCode(); // 예: GlobalErrorCode.UNAUTHORIZED
      message = authEx.getMessage();
    } else {
      // 2) 그 외 예외는 서버 내부 오류로 처리
      errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
      message =
          ex.getMessage() != null && !ex.getMessage().isBlank()
              ? ex.getMessage()
              : "Unexpected server error";
    }

    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(errorCode.getHttpStatus());
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // message는 detail 쪽에 넣어줌 (기본 메시지는 errorCode에 있음)
    ErrorResponse body = ErrorResponse.from(errorCode, message);

    byte[] bytes = toJsonBytes(body);
    var buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  /**
   * ErrorResponse를 JSON 바이트 배열로 직렬화한다. 직렬화 실패 시에는 간단한 fallback JSON을 사용한다.
   *
   * @param body JSON으로 직렬화할 객체
   * @return 직렬화된 JSON 바이트 배열
   */
  private byte[] toJsonBytes(Object body) {
    try {
      return objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      String fallback =
          """
              {"code":"GATEWAY-500","message":"Failed to serialize error response"}
              """;
      return fallback.getBytes(StandardCharsets.UTF_8);
    }
  }
}
