package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.Owner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<Owner, UUID> {

  Optional<Owner> findByUserId(UUID userId);
}
