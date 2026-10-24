package com.zjgsu.sfy.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** identity 模块对外 API。其他模块只允许经本接口读取用户与角色，禁止直接访问其 Repository / Entity。 */
public interface IdentityApi {

  Optional<UserProfile> findUser(UUID userId);

  java.util.Map<UUID, UserProfile> findUsers(Collection<UUID> userIds);

  Optional<UserProfile> findUserByUsername(String username);

  /**
   * @throws com.zjgsu.sfy.common.error.BusinessException {@code RESOURCE_NOT_FOUND} 用户不存在
   */
  UserProfile getUser(UUID userId);

  /**
   * 返回拥有任一指定角色的启用用户 id。角色名与 JWT 的 {@code roles} 声明一致（如 {@code PHARMACY}、 {@code
   * ADMIN}），未知角色名被忽略；供通知等模块按角色广播，不向外暴露内部角色枚举。
   */
  List<UUID> findActiveUserIdsInRoles(Collection<String> roleNames);

  /**
   * @throws com.zjgsu.sfy.common.error.BusinessException {@code AUTH_UNAUTHENTICATED} 当前请求未认证
   */
  CurrentUser currentUser();
}
