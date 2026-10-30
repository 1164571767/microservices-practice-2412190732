package com.zjgsu.sfy.identity;

import com.zjgsu.sfy.identity.common.Role;
import java.util.Set;
import java.util.UUID;

/** 当前请求已认证用户的身份声明。角色类型不外泄，其他模块只能调用语义谓词。 */
public record CurrentUser(UUID userId, String username, Set<Role> roles) {

  public CurrentUser {
    roles = Set.copyOf(roles);
  }

  /** 员工 / 医生 / 管理员：可操作任意资源。 */
  public boolean isClinicStaff() {
    return roles.contains(Role.DOCTOR) || roles.contains(Role.STAFF) || roles.contains(Role.ADMIN);
  }

  /** 药房 / 管理员：可维护药品目录、调整库存与发药。 */
  public boolean isPharmacyStaff() {
    return roles.contains(Role.PHARMACY) || roles.contains(Role.ADMIN);
  }

  /** 前台 / 管理员：可生成账单、退款、作废与维护收费项目。 */
  public boolean isBillingStaff() {
    return roles.contains(Role.STAFF) || roles.contains(Role.ADMIN);
  }

  public boolean isAdmin() {
    return roles.contains(Role.ADMIN);
  }

  public boolean isDoctor() {
    return roles.contains(Role.DOCTOR);
  }
}
