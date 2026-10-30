package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.Owner;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface OwnerRepository extends JpaRepository<Owner, UUID> {
  Optional<Owner> findByUserId(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Owner o where o.id = :id")
  Optional<Owner> lockById(UUID id);

  @Query(
      """
    select o from Owner o where (:archived is null or (case when o.archivedAt is null then false else true end) = :archived)
      and (lower(o.name) like :pattern escape '!' or o.phone like :pattern escape '!'
        or exists (select p.id from Pet p where p.ownerId = o.id and lower(p.name) like :pattern escape '!'))
    """)
  Page<Owner> search(String pattern, Boolean archived, Pageable pageable);
}
