package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.Vaccination;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaccinationRepository extends JpaRepository<Vaccination, UUID> {

  List<Vaccination> findByPetIdOrderByAdministeredOnDesc(UUID petId);

  boolean existsBySupersedesId(UUID supersedesId);
}
