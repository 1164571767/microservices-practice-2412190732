package com.zjgsu.sfy.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 可选的首个管理员引导配置。仅当 username 与 password 均已提供时生效，用于全新环境获得初始管理员。 */
@ConfigurationProperties(prefix = "petcare.security.bootstrap-admin")
public record BootstrapAdminProperties(
    String username, String password, String phone, String displayName) {

  private static final String DEFAULT_DISPLAY_NAME = "系统管理员";

  public BootstrapAdminProperties {
    displayName = StringUtils.hasText(displayName) ? displayName : DEFAULT_DISPLAY_NAME;
  }

  public boolean enabled() {
    return StringUtils.hasText(username)
        && StringUtils.hasText(password)
        && StringUtils.hasText(phone);
  }
}
