package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.Pet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PetRepository extends JpaRepository<Pet, UUID> {
  @Query(
      "select p.id from Pet p, Owner o where p.ownerId = o.id and (lower(p.name) like :pattern escape '!' or lower(o.name) like :pattern escape '!')")
  java.util.List<UUID> searchPatientIds(String pattern, Pageable pageable);

  Page<Pet> findByOwnerId(UUID ownerId, Pageable pageable);

  long countByOwnerId(UUID ownerId);

  @Query("select p.ownerId from Pet p where p.id = :id")
  Optional<UUID> findOwnerId(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Pet p where p.id = :id")
  Optional<Pet> lockById(UUID id);

  @Query(
      """
    select p from Pet p, Owner o where p.ownerId = o.id
      and (:ownerId is null or p.ownerId = :ownerId)
      and (:archived is null or (case when p.archivedAt is not null or o.archivedAt is not null then true else false end) = :archived)
      and (lower(p.name) like :pattern escape '!' or lower(o.name) like :pattern escape '!' or o.phone like :pattern escape '!')
    """)
  Page<Pet> search(String pattern, Boolean archived, UUID ownerId, Pageable pageable);
}
