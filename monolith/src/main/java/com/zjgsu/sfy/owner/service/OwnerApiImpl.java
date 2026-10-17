package com.zjgsu.sfy.owner.service;

import com.zjgsu.sfy.owner.OwnerApi;
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

  public OwnerApiImpl(PetRepository petRepository, OwnerRepository ownerRepository) {
    this.petRepository = petRepository;
    this.ownerRepository = ownerRepository;
  }

  @Override
  public Optional<PetSummary> findPet(UUID petId) {
    return petRepository
        .findById(petId)
        .map(pet -> new PetSummary(pet.getId(), pet.getOwnerId(), pet.getName(), pet.getSpecies()));
  }

  @Override
  public Optional<UUID> findOwnerIdByUser(UUID userId) {
    return ownerRepository.findByUserId(userId).map(owner -> owner.getId());
  }
}
