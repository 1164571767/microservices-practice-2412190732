package com.zjgsu.sfy.owner;

import java.util.Optional;
import java.util.UUID;

/** owner 模块对外 API。其他模块经此只读获取宠物档案，禁止直接访问其 Repository / Entity。 */
public interface OwnerApi {

  Optional<PetSummary> findPet(UUID petId);

  /** 按 identity userId 查所属主人档案 id，供其他模块做归属校验。 */
  Optional<UUID> findOwnerIdByUser(UUID userId);
}
