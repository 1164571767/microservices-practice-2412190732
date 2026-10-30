package com.zjgsu.sfy.pharmacy.repository;

import com.zjgsu.sfy.pharmacy.entity.Drug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrugRepository extends JpaRepository<Drug, UUID> {

  Optional<Drug> findByNameAndSpecification(String name, String specification);

  boolean existsByNameAndSpecificationAndIdNot(String name, String specification, UUID id);

  /** 处方明细未指定规格时，按名称查找；存在多个规格时由调用方拒绝。 */
  List<Drug> findByName(String name);

  @Query(
      "select d from Drug d"
          + " where lower(d.name) like lower(:pattern) escape '!'"
          + " or lower(d.specification) like lower(:pattern) escape '!'")
  Page<Drug> search(@Param("pattern") String pattern, Pageable pageable);
}
