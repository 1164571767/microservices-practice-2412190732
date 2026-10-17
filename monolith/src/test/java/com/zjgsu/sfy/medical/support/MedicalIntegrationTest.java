package com.zjgsu.sfy.medical.support;

import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import com.zjgsu.sfy.medical.repository.MedicalRecordRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionItemRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** medical 模块集成测试基类：复用预约/主人夹具与真实 MySQL。 */
public abstract class MedicalIntegrationTest extends AppointmentIntegrationTest {

  @Autowired protected MedicalRecordRepository medicalRecordRepository;
  @Autowired protected PrescriptionRepository prescriptionRepository;
  @Autowired protected PrescriptionItemRepository prescriptionItemRepository;

  protected User seedDoctor() {
    return seedUser(Set.of(Role.DOCTOR));
  }

  protected String recordBody(
      UUID appointmentId, String symptoms, String diagnosis, String advice, Instant visitedAt) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("appointmentId", appointmentId.toString());
    body.put("symptoms", symptoms);
    body.put("diagnosis", diagnosis);
    body.put("advice", advice);
    body.put("visitedAt", visitedAt == null ? null : visitedAt.toString());
    return objectMapper.writeValueAsString(body);
  }

  protected String prescriptionBody(List<Map<String, Object>> items) {
    return objectMapper.writeValueAsString(Map.of("items", items));
  }

  protected Map<String, Object> line(String drugName, int quantity, int days) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("drugName", drugName);
    item.put("specification", "0.5g");
    item.put("dosage", "1片");
    item.put("frequency", "每日2次");
    item.put("quantity", quantity);
    item.put("days", days);
    item.put("note", null);
    return item;
  }

  /** 直接落库一份病历（历史查询测试用，跳过写侧流程）。 */
  protected MedicalRecord seedRecord(
      UUID petId, UUID doctorId, Instant visitedAt, String diagnosis) {
    return medicalRecordRepository.saveAndFlush(
        MedicalRecord.create(UUID.randomUUID(), petId, doctorId, "症状", diagnosis, null, visitedAt));
  }

  /** 为病历落库一张含一行明细的处方。 */
  protected Prescription seedPrescription(UUID recordId, UUID doctorId, String drugName) {
    Prescription prescription =
        prescriptionRepository.saveAndFlush(Prescription.issue(recordId, doctorId));
    prescriptionItemRepository.saveAndFlush(
        PrescriptionItem.create(prescription.getId(), drugName, "0.5g", "1片", "每日2次", 3, 5, null));
    return prescription;
  }
}
