package com.zjgsu.sfy.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
  AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未认证或凭据无效"),
  AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
  AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行该操作"),
  USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "用户名或手机号已被使用"),
  APPOINTMENT_CONFLICT(HttpStatus.CONFLICT, "该时段已被预约"),
  APPOINTMENT_NOT_AVAILABLE(HttpStatus.CONFLICT, "该时段不在可预约范围内"),
  APPOINTMENT_INVALID_STATE(HttpStatus.CONFLICT, "当前预约状态不允许该操作"),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "幂等键已被使用且请求不一致"),
  OPERATION_NOT_ALLOWED(HttpStatus.CONFLICT, "当前业务状态不允许该操作"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

  private final HttpStatus status;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String defaultMessage) {
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus status() {
    return status;
  }

  public String defaultMessage() {
    return defaultMessage;
  }
}
