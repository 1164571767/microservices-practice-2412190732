package com.zjgsu.sfy.medical.util;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

/** 判断 {@link DataIntegrityViolationException} 是否由唯一约束冲突（MySQL error code 1062）引起。 */
public final class UniqueViolations {

  private static final int MYSQL_DUPLICATE_ENTRY = 1062;

  private UniqueViolations() {}

  public static boolean causedByUniqueConstraint(DataIntegrityViolationException ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException
          && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
        return true;
      }
    }
    return false;
  }
}
