package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
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
    Optional<User> found =
        userRepository.findByUsername(User.normalizeUsername(command.username()));
    if (found.isEmpty()) {
      passwordEncoder.matches(command.password(), dummyPasswordHash);
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    User user = found.get();
    if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    log.info("identity.user.login userId={}", user.getId());
    return issueTokens(user);
  }

  /** 轮换 refresh token：旧 token 原子撤销后签发新的一对 token。 */
  @Transactional
  public TokenPair refresh(String rawRefreshToken) {
    Instant now = Instant.now();
    String currentHash = tokenService.hash(rawRefreshToken);
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHash(currentHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
    if (!current.isActiveAt(now)) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    User user =
        userRepository
            .findById(current.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    String newRawRefresh = tokenService.newRefreshToken();
    String newHash = tokenService.hash(newRawRefresh);
    int revoked = refreshTokenRepository.revokeIfActive(currentHash, now, newHash);
    if (revoked == 0) {
      // 旧 token 已被并发请求消费：拒绝并视为可能的 token 重放。
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    log.info("identity.token.rotated userId={}", user.getId());
    return issueTokens(user, newRawRefresh);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenRepository.revokeIfActive(tokenService.hash(rawRefreshToken), Instant.now(), null);
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
