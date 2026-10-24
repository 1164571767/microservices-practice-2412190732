package com.zjgsu.sfy.identity.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 系统角色；对外暴露的稳定标识，供其他模块做角色判断。 */
@Schema(description = "账号角色：OWNER 宠物主人 / DOCTOR 医生 / STAFF 前台 / PHARMACY 药房 / ADMIN 管理员")
public enum Role {
  OWNER,
  DOCTOR,
  STAFF,
  PHARMACY,
  ADMIN
}
