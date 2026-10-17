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

  public PetService(PetRepository petRepository, OwnerRepository ownerRepository) {
    this.petRepository = petRepository;
    this.ownerRepository = ownerRepository;
  }

  @Transactional
  public Pet createPet(CurrentUser requester, UpsertPetCommand command) {
    Owner owner = requireOwnOwner(requester);
    Pet pet =
        Pet.create(
            owner.getId(),
            command.name(),
            command.species(),
            command.birthDate(),
            command.weightKg());
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

  @Transactional
  public Pet updatePet(CurrentUser requester, UUID petId, UpsertPetCommand command) {
    Pet pet = requireOwnedPet(requester, petId);
    pet.updateDetails(command.name(), command.species(), command.birthDate(), command.weightKg());
    return pet;
  }

  @Transactional
  public void deletePet(CurrentUser requester, UUID petId) {
    petRepository.delete(requireOwnedPet(requester, petId));
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
    Pet pet = loadPet(petId);
    if (!loadPetOwner(pet).belongsTo(requester.userId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
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
