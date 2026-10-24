package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.RefreshToken;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.RefreshTokenRepository;
import com.zjgsu.sfy.identity.repository.UserRepository;
import com.zjgsu.sfy.identity.service.command.LoginCommand;
import com.zjgsu.sfy.identity.service.command.RegisterCommand;
import com.zjgsu.sfy.identity.service.result.TokenPair;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final String dummyPasswordHash;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      TokenService tokenService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    // 用于用户名不存在时执行等价的哈希校验，降低通过响应时间枚举账号的风险。
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /** 公开注册固定为 OWNER，避免自助获得员工/管理员权限。 */
  @Transactional
  public UserProfile register(RegisterCommand command) {
    String username = User.normalizeUsername(command.username());
    String phone = command.phone().trim();
    if (userRepository.existsByUsername(username) || userRepository.existsByPhone(phone)) {
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }
    User user =
        User.create(
            username,
            phone,
            passwordEncoder.encode(command.password()),
            command.displayName(),
            Set.of(Role.OWNER));
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      // 并发注册时以数据库唯一约束为最终守卫。
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }
    log.info("identity.user.registered userId={}", user.getId());
    return UserProfiles.of(user);
  }

  @Transactional
  public TokenPair login(LoginCommand command) {
    String username = User.normalizeUsername(command.username());
    Optional<User> found = userRepository.findLockedByUsername(username);
    if (found.isEmpty()) {
      passwordEncoder.matches(command.password(), dummyPasswordHash);
      // 失败登录留审计线索（不记录密码）；限流在网关/后续迭代补充。
      log.warn("identity.user.login_failed username={} reason=unknown_user", username);
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    User user = found.get();
    if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
      log.warn("identity.user.login_failed userId={} reason=bad_password", user.getId());
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    if (!user.isActive()) {
      log.warn("identity.user.login_failed userId={} reason=disabled", user.getId());
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    log.info("identity.user.login userId={}", user.getId());
    return issueTokens(user);
  }

  /**
   * 轮换 refresh token：旧 token 原子撤销后签发新的一对 token。
   *
   * <p>重放检测命中时需要在返回 401 的同时保留「撤销 token family」这一安全副作用， 因此对 {@link BusinessException} 不回滚；其余异常仍正常回滚。
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public TokenPair refresh(String rawRefreshToken) {
    Instant now = Instant.now();
    String currentHash = tokenService.hash(rawRefreshToken);
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHash(currentHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
    if (!current.isActiveAt(now)) {
      if (current.getRevokedAt() != null) {
        // 已撤销 token 被再次使用 = 重放：撤销该用户全部活跃 token（token family），强制重新登录。
        int revokedFamily = refreshTokenRepository.revokeAllActiveForUser(current.getUserId(), now);
        log.warn(
            "identity.token.reuse_detected userId={} revokedFamily={}",
            current.getUserId(),
            revokedFamily);
      }
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    User user =
        userRepository
            .findLockedById(current.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    String newRawRefresh = tokenService.newRefreshToken();
    String newHash = tokenService.hash(newRawRefresh);
    int revoked = refreshTokenRepository.revokeIfActive(currentHash, now, newHash);
    if (revoked == 0) {
      // 旧 token 已被并发请求消费或遭重放：撤销该用户全部活跃 refresh token，
      // 避免攻击者用已轮换出的新 token 继续会话（token family revocation）。
      int revokedFamily = refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
      log.warn(
          "identity.token.reuse_detected userId={} revokedFamily={}", user.getId(), revokedFamily);
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    log.info("identity.token.rotated userId={}", user.getId());
    return issueTokens(user, newRawRefresh);
  }

  @Transactional
  public void logout(CurrentUser requester, String rawRefreshToken) {
    // 只撤销本人名下 token：即使拿到他人 refresh token 也不能强制其下线。
    refreshTokenRepository.revokeIfActiveForUser(
        tokenService.hash(rawRefreshToken), requester.userId(), Instant.now());
  }

  private TokenPair issueTokens(User user) {
    return issueTokens(user, tokenService.newRefreshToken());
  }

  private TokenPair issueTokens(User user, String rawRefreshToken) {
    refreshTokenRepository.save(
        new RefreshToken(
            user.getId(),
            tokenService.hash(rawRefreshToken),
            Instant.now().plus(tokenService.refreshTtl())));
    String accessToken = tokenService.issueAccessToken(user);
    return new TokenPair(
        accessToken, rawRefreshToken, "Bearer", tokenService.accessTtl().toSeconds());
  }
}
