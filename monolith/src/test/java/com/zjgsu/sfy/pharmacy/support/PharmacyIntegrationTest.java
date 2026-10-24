package com.zjgsu.sfy.pharmacy.support;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.pharmacy.entity.DispenseRecord;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.entity.Inventory;
import com.zjgsu.sfy.pharmacy.repository.DispenseItemRepository;
import com.zjgsu.sfy.pharmacy.repository.DispenseRecordRepository;
import com.zjgsu.sfy.pharmacy.repository.DrugRepository;
import com.zjgsu.sfy.pharmacy.repository.InventoryAdjustmentRepository;
import com.zjgsu.sfy.pharmacy.repository.InventoryRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** pharmacy 模块集成测试基类：复用 identity / owner / appointment / medical 夹具与真实 MySQL。 */
public abstract class PharmacyIntegrationTest extends MedicalIntegrationTest {

  protected static final String DEFAULT_SPEC = "0.5g";

  @Autowired protected DrugRepository drugRepository;
  @Autowired protected InventoryRepository inventoryRepository;
  @Autowired protected InventoryAdjustmentRepository inventoryAdjustmentRepository;
  @Autowired protected DispenseRecordRepository dispenseRecordRepository;
  @Autowired protected DispenseItemRepository dispenseItemRepository;

  protected User seedPharmacist() {
    return seedUser(Set.of(Role.PHARMACY));
  }

  protected String uniqueDrugName() {
    return "药" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  protected Drug seedDrug(String name, String specification, int threshold, int initialQuantity) {
    return seedDrug(name, specification, "盒", threshold, initialQuantity, true);
  }

  protected Drug seedDrug(
      String name,
      String specification,
      String unit,
      int threshold,
      int initialQuantity,
      boolean active) {
    // 真实 MySQL 不随测试回滚；名称加随机后缀保证用例可重复执行。
    String uniqueName = name + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    Drug drug = Drug.create(uniqueName, specification, unit, threshold);
    if (!active) {
      drug.updateDetails(uniqueName, specification, unit, threshold, false);
    }
    drug = drugRepository.saveAndFlush(drug);
    inventoryRepository.saveAndFlush(Inventory.initialize(drug.getId(), initialQuantity));
    return drug;
  }

  protected Prescription seedPrescriptionWithQuantity(
      UUID recordId, UUID doctorId, String drugName, String specification, int quantity) {
    Prescription prescription =
        prescriptionRepository.saveAndFlush(Prescription.issue(recordId, doctorId));
    prescriptionItemRepository.saveAndFlush(
        PrescriptionItem.create(
            prescription.getId(), drugName, specification, "1片", "每日2次", quantity, 5, null));
    return prescription;
  }

  protected Prescription seedPrescriptionFor(User doctor, Pet pet, String drugName, int quantity) {
    MedicalRecord record =
        seedRecord(pet.getId(), doctor.getId(), Instant.now().minusSeconds(60), "诊断");
    return seedPrescriptionWithQuantity(
        record.getId(), doctor.getId(), drugName, DEFAULT_SPEC, quantity);
  }

  protected Prescription seedPrescriptionFor(
      User doctor, Pet pet, String drugName, String specification, int quantity) {
    MedicalRecord record =
        seedRecord(pet.getId(), doctor.getId(), Instant.now().minusSeconds(60), "诊断");
    return seedPrescriptionWithQuantity(
        record.getId(), doctor.getId(), drugName, specification, quantity);
  }

  protected Pet seedPetWithOwner() {
    return seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
  }

  protected Appointment seedAppointmentFor(Pet pet, User doctor) {
    return seedAppointment(pet.getId(), doctor.getId());
  }

  protected String drugBody(
      String name, String specification, String unit, int threshold, int initialQuantity) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("specification", specification);
    body.put("unit", unit);
    body.put("lowStockThreshold", threshold);
    body.put("initialQuantity", initialQuantity);
    return objectMapper.writeValueAsString(body);
  }

  protected String updateDrugBody(
      String name, String specification, String unit, int threshold, boolean active) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("specification", specification);
    body.put("unit", unit);
    body.put("lowStockThreshold", threshold);
    body.put("active", active);
    return objectMapper.writeValueAsString(body);
  }

  protected String adjustBody(int delta, String reason) {
    return objectMapper.writeValueAsString(Map.of("delta", delta, "reason", reason));
  }

  protected int stockOf(UUID drugId) {
    return inventoryRepository.findById(drugId).orElseThrow().getQuantity();
  }

  protected long dispenseCountFor(UUID... prescriptionIds) {
    Set<UUID> ids = Set.of(prescriptionIds);
    return dispenseRecordRepository.findAll().stream()
        .filter(record -> ids.contains(record.getPrescriptionId()))
        .count();
  }

  protected DispenseRecord findDispense(UUID prescriptionId) {
    return dispenseRecordRepository.findByPrescriptionId(prescriptionId).orElseThrow();
  }
}
