package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.query.LikePatterns;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import com.zjgsu.sfy.identity.entity.AccountAudit;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.*;
import com.zjgsu.sfy.identity.service.command.UpdateEmployeeCommand;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeAccountService {
  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final AccountAuditRepository audits;
  private final PasswordEncoder passwords;

  public EmployeeAccountService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      AccountAuditRepository audits,
      PasswordEncoder passwords) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.audits = audits;
    this.passwords = passwords;
  }

  @Transactional(readOnly = true)
  public Page<User> list(
      CurrentUser actor, String query, UserStatus status, Role role, Pageable pageable) {
    admin(actor);
    if (role == Role.OWNER) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    return users.findEmployees(LikePatterns.contains(query), status, role, pageable);
  }

  @Transactional
  public User update(CurrentUser actor, UUID id, UpdateEmployeeCommand command) {
    management(actor);
    User user = employee(id);
    version(user, command.version());
    if (command.roles() == null
        || command.roles().isEmpty()
        || command.roles().stream().allMatch(r -> r == Role.OWNER)
        || command.roles().contains(Role.OWNER) != user.getRoles().contains(Role.OWNER))
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    boolean securityChange =
        !user.getRoles().equals(command.roles()) || user.getStatus() != command.status();
    if (actor.userId().equals(id) && securityChange)
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "不能修改自己的角色或停用自己的账号");
    if (user.isActive()
        && user.getRoles().contains(Role.ADMIN)
        && (command.status() != UserStatus.ACTIVE || !command.roles().contains(Role.ADMIN))
        && users.countByStatusAndRole(UserStatus.ACTIVE, Role.ADMIN) <= 1)
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "至少保留一名启用的管理员");
    String phone = command.phone().trim();
    if (phone.length() > 20) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    if (users.findByPhone(phone).filter(other -> !other.getId().equals(id)).isPresent())
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    String before = summary(user);
    user.updateEmployee(command.displayName(), phone, command.roles(), command.status());
    if (securityChange) revoke(user);
    try {
      users.flush();
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }
    audits.save(
        new AccountAudit(
            id, actor.userId(), "UPDATE_EMPLOYEE", command.reason().trim(), before, summary(user)));
    return user;
  }

  @Transactional
  public void changePassword(CurrentUser actor, String oldPassword, String newPassword) {
    User user = employee(actor.userId());
    if (!user.isActive()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    if (oldPassword.getBytes(StandardCharsets.UTF_8).length > 72
        || !passwords.matches(oldPassword, user.getPasswordHash()))
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前密码不正确");
    setPassword(user, newPassword);
    audits.save(
        new AccountAudit(user.getId(), actor.userId(), "CHANGE_PASSWORD", "员工自助修改密码", null, null));
  }

  @Transactional
  public void resetPassword(
      CurrentUser actor, UUID id, long version, String password, String reason) {
    management(actor);
    if (actor.userId().equals(id))
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "请通过修改密码入口修改自己的密码");
    User user = employee(id);
    version(user, version);
    setPassword(user, password);
    audits.save(new AccountAudit(id, actor.userId(), "RESET_PASSWORD", reason.trim(), null, null));
  }

  @Transactional(readOnly = true)
  public Page<AccountAudit> history(CurrentUser actor, UUID id, Pageable pageable) {
    admin(actor);
    User user =
        users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (user.getRoles().stream().allMatch(r -> r == Role.OWNER))
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    return audits.findByTargetUserId(id, pageable);
  }

  private void setPassword(User user, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码最多 72 个 UTF-8 字节，请缩短后重试");
    if (passwords.matches(password, user.getPasswordHash()))
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新密码不能与当前密码相同");
    user.changePassword(passwords.encode(password));
    revoke(user);
  }

  private void revoke(User user) {
    refreshTokens.revokeAllActiveForUser(user.getId(), Instant.now());
  }

  private User employee(UUID id) {
    User user =
        users
            .findLockedById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (user.getRoles().stream().allMatch(r -> r == Role.OWNER))
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    return user;
  }

  private void version(User user, long version) {
    if (user.getVersion() != version)
      throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
  }

  private void admin(CurrentUser actor) {
    if (!actor.isAdmin()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }

  private void management(CurrentUser actor) {
    admin(actor);
    users.lockManagement();
    User administrator =
        users
            .findById(actor.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FORBIDDEN));
    if (!administrator.isActive() || !administrator.getRoles().contains(Role.ADMIN))
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }

  private String summary(User user) {
    return "姓名="
        + user.getDisplayName()
        + "；手机="
        + user.getPhone()
        + "；角色="
        + user.getRoles().stream().map(Enum::name).sorted().toList()
        + "；状态="
        + user.getStatus();
  }
}
