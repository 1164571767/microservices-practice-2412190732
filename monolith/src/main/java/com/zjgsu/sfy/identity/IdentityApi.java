package com.zjgsu.sfy.identity;

import java.util.Optional;
import java.util.UUID;

/** identity 模块对外 API。其他模块只允许经本接口读取用户与角色，禁止直接访问其 Repository / Entity。 */
public interface IdentityApi {

  Optional<UserProfile> findUser(UUID userId);

  /**
   * @throws com.zjgsu.sfy.common.error.BusinessException {@code RESOURCE_NOT_FOUND} 用户不存在
   */
  UserProfile getUser(UUID userId);

  /**
   * @throws com.zjgsu.sfy.common.error.BusinessException {@code AUTH_UNAUTHENTICATED} 当前请求未认证
   */
  CurrentUser currentUser();
}
