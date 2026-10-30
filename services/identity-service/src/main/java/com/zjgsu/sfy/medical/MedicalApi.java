package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** medical 模块对外 API。其他模块只能经此只读获取病历 / 处方，禁止直接访问其表。 */
public interface MedicalApi {

  java.util.Map<UUID, ConsultationSummary> consultationSummaries(
      java.util.Collection<UUID> appointmentIds);

  Optional<PharmacyPrescription> pharmacyPrescription(UUID prescriptionId);

  org.springframework.data.domain.Page<BillingRecordSummary> billingRecords(
      UUID petId, org.springframework.data.domain.Pageable pageable);

  /** 返回某只宠物的病历索引（按就诊时间倒序）；无记录返回空列表。 */
  List<MedicalRecordSummary> findRecordsByPet(UUID petId);

  /** 按病历 id 返回只读索引；供 billing 等下游模块校验病历归属，不暴露实体。 */
  Optional<MedicalRecordSummary> findRecord(UUID recordId);

  /**
   * 返回就诊时间落在 {@code [fromInclusive, toExclusive)} 内的病历索引（按就诊时间升序）。
   *
   * <p>供 notification 等下游模块按时间窗口聚合（如复诊提醒），不暴露实体与仓储。
   */
  List<MedicalRecordSummary> findRecordsVisitedBetween(Instant fromInclusive, Instant toExclusive);

  /**
   * 按处方 id 返回处方自包含视图（含明细行），供 pharmacy 发药时读取药品与数量。
   *
   * @return 处方不存在（或所属病历不存在）时返回空。
   */
  Optional<PrescriptionSummary> findPrescription(UUID prescriptionId);
}
