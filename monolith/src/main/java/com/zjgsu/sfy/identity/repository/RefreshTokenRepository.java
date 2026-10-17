package com.zjgsu.sfy.identity.repository;

import com.zjgsu.sfy.identity.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** 原子地把仍有效的 token 标记为已撤销。返回受影响行数：0 表示该 token 已被撤销或已被并发消费。 */
  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now, t.replacedBy = :replacedBy "
          + "where t.tokenHash = :tokenHash and t.revokedAt is null")
  int revokeIfActive(
      @Param("tokenHash") String tokenHash,
      @Param("now") Instant now,
      @Param("replacedBy") String replacedBy);
}
