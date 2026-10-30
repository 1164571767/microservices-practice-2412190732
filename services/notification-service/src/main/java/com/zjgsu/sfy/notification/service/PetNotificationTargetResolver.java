package com.zjgsu.sfy.notification.service;

import com.zjgsu.sfy.owner.OwnerApi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 经 {@link OwnerApi} 解析宠物通知的接收人；owner 或主人档案缺失时返回空，由调用方跳过并记录。 */
@Service
public class PetNotificationTargetResolver {

  private final OwnerApi ownerApi;

  public PetNotificationTargetResolver(OwnerApi ownerApi) {
    this.ownerApi = ownerApi;
  }

  public Optional<PetNotificationTarget> resolve(UUID petId) {
    return ownerApi
        .findPet(petId)
        .flatMap(
            pet ->
                ownerApi
                    .findUserIdByPet(petId)
                    .map(userId -> new PetNotificationTarget(userId, pet.name())));
  }
}
