package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.Allergy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyRepository extends JpaRepository<Allergy, UUID> {

  List<Allergy> findByPetIdOrderByCreatedAtAsc(UUID petId);
}
