package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.Pet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, UUID> {

  Page<Pet> findByOwnerId(UUID ownerId, Pageable pageable);

  long countByOwnerId(UUID ownerId);
}
