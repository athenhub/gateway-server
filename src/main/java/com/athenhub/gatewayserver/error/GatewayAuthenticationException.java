package com.athenhub.gatewayserver.error;

/**
 * Gateway에서 인증 관련 오류가 발생했을 때 사용하는 예외.
 *
 * <p>예: JWT에 Slack ID가 없거나, 토큰이 유효하지 않을 때 등.
 */
public class GatewayAuthenticationException extends RuntimeException {

  private final GlobalErrorCode errorCode;

  /**
   * 공통 에러 코드를 포함하는 생성자.
   *
   * @param errorCode 사용할 에러 코드 (예: GlobalErrorCode.UNAUTHORIZED)
   * @param message 예외 메시지
   */
  public GatewayAuthenticationException(GlobalErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * 이 예외에 매핑된 에러 코드를 반환한다.
   *
   * @return 에러 코드
   */
  public GlobalErrorCode getErrorCode() {
    return errorCode;
  }
}
