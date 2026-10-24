package com.zjgsu.sfy.owner.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.owner.entity.Owner;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.owner.repository.OwnerRepository;
import com.zjgsu.sfy.owner.repository.PetRepository;
import com.zjgsu.sfy.owner.service.command.UpsertPetCommand;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 宠物档案用例：主人管理自己的宠物，员工可只读；归属校验以数据库 owner_id 为准。 */
@Service
public class PetService {

  private final PetRepository petRepository;
  private final OwnerRepository ownerRepository;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public PetService(PetRepository petRepository, OwnerRepository ownerRepository) {
    this.petRepository = petRepository;
    this.ownerRepository = ownerRepository;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Pet createPet(CurrentUser requester, UpsertPetCommand command) {
    Owner owner = requireOwnOwner(requester);
    owner = ownerRepository.lockById(owner.getId()).orElseThrow();
    entityManager.refresh(owner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    owner.requireActive();
    Pet pet =
        Pet.create(
            owner.getId(),
            command.name(),
            command.species(),
            command.birthDate(),
            command.weightKg());
    pet.markBirthDateEstimated(Boolean.TRUE.equals(command.birthDateEstimated()));
    return petRepository.saveAndFlush(pet);
  }

  @Transactional(readOnly = true)
  public Page<Pet> listPets(CurrentUser requester, Pageable pageable) {
    Owner owner = requireOwnOwner(requester);
    return petRepository.findByOwnerId(owner.getId(), pageable);
  }

  @Transactional(readOnly = true)
  public Pet getPet(CurrentUser requester, UUID petId) {
    return requireReadablePet(requester, petId);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Pet updatePet(CurrentUser requester, UUID petId, UpsertPetCommand command) {
    Pet pet = requireOwnedPet(requester, petId);
    pet.updateDetails(command.name(), command.species(), command.birthDate(), command.weightKg());
    if (command.birthDateEstimated() != null)
      pet.markBirthDateEstimated(command.birthDateEstimated());
    return petRepository.saveAndFlush(pet);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public void deletePet(CurrentUser requester, UUID petId) {
    requireReadablePet(requester, petId);
    throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "禁止物理删除档案，请由院内员工归档并保留历史");
  }

  /** 同模块复用：当前用户可读该宠物（本人所有或员工角色）。 */
  Pet requireReadablePet(CurrentUser requester, UUID petId) {
    Pet pet = loadPet(petId);
    if (requester.isClinicStaff()) {
      return pet;
    }
    if (!loadPetOwner(pet).belongsTo(requester.userId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    return pet;
  }

  /** 同模块复用：当前用户必须拥有该宠物。 */
  Pet requireOwnedPet(CurrentUser requester, UUID petId) {
    UUID ownerId =
        petRepository
            .findOwnerId(petId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    Owner owner =
        ownerRepository
            .lockById(ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    entityManager.refresh(owner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    if (!owner.belongsTo(requester.userId())) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    owner.requireActive();
    Pet pet =
        petRepository
            .lockById(petId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    entityManager.refresh(pet, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    pet.requireActive();
    return pet;
  }

  private Owner requireOwnOwner(CurrentUser requester) {
    return ownerRepository
        .findByUserId(requester.userId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "尚未创建主人档案"));
  }

  private Pet loadPet(UUID petId) {
    return petRepository
        .findById(petId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private Owner loadPetOwner(Pet pet) {
    return ownerRepository
        .findById(pet.getOwnerId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }
}
