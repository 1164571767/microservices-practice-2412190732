package com.zjgsu.sfy.owner;

import java.util.Optional;
import java.util.UUID;

/** owner 模块对外 API。查询保留归档历史；预约写入通过同一事务内的活动档案锁校验。 */
public interface OwnerApi {

  java.util.List<PharmacyAllergy> pharmacyAllergies(UUID petId);

  java.util.List<UUID> searchPatientIds(String query);

  java.util.Map<UUID, PatientContactSummary> patientContacts(java.util.Collection<UUID> petIds);

  Optional<PetSummary> findPet(UUID petId);

  void requireActivePetForBooking(UUID petId);

  /**
   * 返回宠物所属主人绑定的 identity 用户 id，供通知等下游模块投递；宠物或主人档案不存在时返回空。
   *
   * <p>identity userId 只经本方法外泄，其他模块无需了解 owner 的表结构。
   */
  Optional<UUID> findUserIdByPet(UUID petId);

  /** 按 identity userId 查所属主人档案 id，供其他模块做归属校验。 */
  Optional<UUID> findOwnerIdByUser(UUID userId);
}
