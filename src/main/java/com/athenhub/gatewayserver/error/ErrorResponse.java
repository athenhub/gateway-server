package com.athenhub.gatewayserver.error;

public record ErrorResponse(String code, String message, String detail) {
  public static ErrorResponse from(GlobalErrorCode errorCode) {
    return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null);
  }

  public static ErrorResponse from(GlobalErrorCode errorCode, String detail) {
    return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), detail);
  }
}
