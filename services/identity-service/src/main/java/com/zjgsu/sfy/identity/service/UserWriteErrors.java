package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

final class UserWriteErrors {
  private UserWriteErrors() {}

  static RuntimeException classify(DataIntegrityViolationException error) {
    // MySQL ER_DUP_ENTRY only. Length, CHECK, NOT NULL and FK violations are not duplicates.
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql
          && sql.getErrorCode() == 1062
          && "23000".equals(sql.getSQLState()))
        return new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }
    return error;
  }
}
