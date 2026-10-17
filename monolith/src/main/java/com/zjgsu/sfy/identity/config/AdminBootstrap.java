package com.zjgsu.sfy.identity.config;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.UserRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 幂等地创建首个管理员；凭据来自环境变量，绝不记录明文。 */
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final BootstrapAdminProperties properties;

  public AdminBootstrap(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      BootstrapAdminProperties properties) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) {
      log.debug("identity.bootstrap-admin.skipped");
      return;
    }
    String username = User.normalizeUsername(properties.username());
    if (userRepository.existsByUsername(username)) {
      log.info("identity.bootstrap-admin.exists username={}", username);
      return;
    }
    User admin =
        User.create(
            username,
            properties.phone(),
            passwordEncoder.encode(properties.password()),
            properties.displayName(),
            Set.of(Role.ADMIN));
    userRepository.save(admin);
    log.info("identity.bootstrap-admin.created username={}", username);
  }
}
