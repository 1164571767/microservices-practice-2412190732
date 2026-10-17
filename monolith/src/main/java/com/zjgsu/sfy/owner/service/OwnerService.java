package com.zjgsu.sfy.owner.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.owner.entity.Owner;
import com.zjgsu.sfy.owner.repository.OwnerRepository;
import com.zjgsu.sfy.owner.service.command.UpsertOwnerCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 主人档案用例：本人自助创建/更新，本人或员工可读。 */
@Service
public class OwnerService {

  private final OwnerRepository ownerRepository;

  public OwnerService(OwnerRepository ownerRepository) {
    this.ownerRepository = ownerRepository;
  }

  @Transactional
  public Owner upsertOwnProfile(CurrentUser requester, UpsertOwnerCommand command) {
    Owner owner = ownerRepository.findByUserId(requester.userId()).orElse(null);
    if (owner == null) {
      owner = Owner.create(requester.userId(), command.name(), command.phone(), command.address());
    } else {
      owner.updateDetails(command.name(), command.phone(), command.address());
    }
    return ownerRepository.saveAndFlush(owner);
  }

  @Transactional(readOnly = true)
  public Owner getOwnProfile(CurrentUser requester) {
    return ownerRepository
        .findByUserId(requester.userId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "尚未创建主人档案"));
  }

  @Transactional(readOnly = true)
  public Owner getProfile(CurrentUser requester, UUID ownerId) {
    Owner owner =
        ownerRepository
            .findById(ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!owner.belongsTo(requester.userId()) && !requester.isClinicStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    return owner;
  }
}
