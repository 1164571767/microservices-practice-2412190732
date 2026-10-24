package com.zjgsu.sfy.owner.service;

import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.owner.PatientContactSummary;
import com.zjgsu.sfy.owner.PetSummary;
import com.zjgsu.sfy.owner.repository.OwnerRepository;
import com.zjgsu.sfy.owner.repository.PetRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OwnerApiImpl implements OwnerApi {

  private final PetRepository petRepository;
  private final OwnerRepository ownerRepository;
  private final com.zjgsu.sfy.owner.repository.AllergyRepository allergies;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public OwnerApiImpl(
      PetRepository petRepository,
      OwnerRepository ownerRepository,
      com.zjgsu.sfy.owner.repository.AllergyRepository allergies) {
    this.petRepository = petRepository;
    this.ownerRepository = ownerRepository;
    this.allergies = allergies;
  }

  @Override
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public java.util.List<com.zjgsu.sfy.owner.PharmacyAllergy> pharmacyAllergies(UUID petId) {
    var records = allergies.findByPetIdOrderByCreatedAtAsc(petId);
    var replaced =
        records.stream()
            .map(a -> a.getSupersedesId())
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    return records.stream()
        .filter(a -> !replaced.contains(a.getId()))
        .map(
            a ->
                new com.zjgsu.sfy.owner.PharmacyAllergy(
                    a.getAllergen(), a.getSeverity().name(), a.getNote()))
        .toList();
  }

  @Override
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public java.util.List<UUID> searchPatientIds(String query) {
    var ids =
        petRepository.searchPatientIds(
            com.zjgsu.sfy.common.query.LikePatterns.contains(query),
            org.springframework.data.domain.PageRequest.of(0, 1001));
    if (ids.size() > 1000)
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.VALIDATION_FAILED, "匹配患者过多，请输入更完整的姓名");
    return ids;
  }

  @Override
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void requireActivePetForBooking(UUID petId) {
    UUID ownerId =
        petRepository
            .findOwnerId(petId)
            .orElseThrow(
                () ->
                    new com.zjgsu.sfy.common.error.BusinessException(
                        com.zjgsu.sfy.common.error.ErrorCode.RESOURCE_NOT_FOUND));
    var owner = ownerRepository.lockById(ownerId).orElseThrow();
    entityManager.refresh(owner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    owner.requireActive();
    var pet = petRepository.lockById(petId).orElseThrow();
    entityManager.refresh(pet, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    pet.requireActive();
  }

  @Override
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public java.util.Map<UUID, PatientContactSummary> patientContacts(
      java.util.Collection<UUID> petIds) {
    if (petIds.isEmpty()) return java.util.Map.of();
    var pets = petRepository.findAllById(petIds);
    var owners =
        ownerRepository
            .findAllById(pets.stream().map(p -> p.getOwnerId()).distinct().toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(o -> o.getId(), o -> o));
    var results = new java.util.HashMap<UUID, PatientContactSummary>();
    for (var pet : pets) {
      var owner = owners.get(pet.getOwnerId());
      if (owner != null)
        results.put(
            pet.getId(),
            new PatientContactSummary(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                owner.getId(),
                owner.getName(),
                owner.getPhone(),
                pet.isArchived(),
                owner.isArchived()));
    }
    return java.util.Map.copyOf(results);
  }

  @Override
  public Optional<PetSummary> findPet(UUID petId) {
    return petRepository
        .findById(petId)
        .map(pet -> new PetSummary(pet.getId(), pet.getOwnerId(), pet.getName(), pet.getSpecies()));
  }

  @Override
  public Optional<UUID> findUserIdByPet(UUID petId) {
    return petRepository
        .findById(petId)
        .flatMap(pet -> ownerRepository.findById(pet.getOwnerId()))
        .map(owner -> owner.getUserId());
  }

  @Override
  public Optional<UUID> findOwnerIdByUser(UUID userId) {
    return ownerRepository.findByUserId(userId).map(owner -> owner.getId());
  }
}
