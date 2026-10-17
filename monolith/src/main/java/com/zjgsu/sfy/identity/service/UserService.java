package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.UserRepository;
import com.zjgsu.sfy.identity.service.command.CreateUserCommand;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /** 由管理员创建任意角色的账号（员工 / 医生 / 管理员）。 */
  @Transactional
  public UserProfile createUser(CreateUserCommand command) {
    String username = User.normalizeUsername(command.username());
    String phone = command.phone().trim();
    if (userRepository.existsByUsername(username) || userRepository.existsByPhone(phone)) {
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }
    Set<Role> roles =
        command.roles() == null || command.roles().isEmpty()
            ? Set.of(Role.OWNER)
            : Set.copyOf(command.roles());
    User user =
        User.create(
            username,
            phone,
            passwordEncoder.encode(command.password()),
            command.displayName(),
            roles);
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }
    log.info("identity.user.createdByAdmin userId={}", user.getId());
    return UserProfiles.of(user);
  }

  /** 本人可读自己档案；员工 / 医生 / 管理员可读他人档案，其余越权。 */
  @Transactional(readOnly = true)
  public UserProfile getProfile(CurrentUser requester, UUID targetUserId) {
    boolean self = requester.userId().equals(targetUserId);
    if (!self && !requester.isClinicStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    return UserProfiles.of(user);
  }
}
