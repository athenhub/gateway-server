package com.athenhub.gatewayserver.error;

import org.springframework.http.HttpStatus;

public enum GlobalErrorCode {
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "GATEWAY-401", "인증 정보가 유효하지 않습니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "GATEWAY-403", "해당 리소스에 접근 권한이 없습니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "GATEWAY-401-01", "토큰이 유효하지 않습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY-500", "예상치 못한 서버 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  GlobalErrorCode(HttpStatus httpStatus, String code, String message) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.message = message;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
