package com.zjgsu.sfy.billing.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.entity.PaymentRecord;
import com.zjgsu.sfy.billing.repository.BillItemRepository;
import com.zjgsu.sfy.billing.repository.BillOperationRepository;
import com.zjgsu.sfy.billing.repository.BillRepository;
import com.zjgsu.sfy.billing.repository.ChargeItemRepository;
import com.zjgsu.sfy.billing.repository.PaymentRecordRepository;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** billing 模块集成测试基类：复用 identity / owner / medical 夹具与真实 MySQL。 */
public abstract class BillingIntegrationTest extends MedicalIntegrationTest {

  @Autowired protected ChargeItemRepository chargeItemRepository;
  @Autowired protected BillRepository billRepository;
  @Autowired protected BillItemRepository billItemRepository;
  @Autowired protected PaymentRecordRepository paymentRecordRepository;
  @Autowired protected BillOperationRepository billOperationRepository;

  protected User seedStaff() {
    return seedUser(Set.of(Role.STAFF));
  }

  protected User seedAdmin() {
    return seedUser(Set.of(Role.ADMIN));
  }

  protected Pet seedPetWithOwner() {
    return seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
  }

  protected MedicalRecord seedRecordFor(Pet pet, User doctor) {
    return seedRecord(pet.getId(), doctor.getId(), Instant.now().minusSeconds(60), "诊断");
  }

  protected String uniqueChargeItemName() {
    return "收费项" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  protected ChargeItem seedChargeItem(String amount) {
    return seedChargeItem(uniqueChargeItemName(), amount);
  }

  protected ChargeItem seedChargeItem(String name, String amount) {
    return chargeItemRepository.saveAndFlush(ChargeItem.create(name, new BigDecimal(amount)));
  }

  protected String chargeItemBody(String name, String amount) {
    return objectMapper.writeValueAsString(Map.of("name", name, "amount", new BigDecimal(amount)));
  }

  protected String updateChargeItemBody(String name, String amount, boolean active) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("amount", new BigDecimal(amount));
    body.put("active", active);
    return objectMapper.writeValueAsString(body);
  }

  protected String billBody(
      UUID medicalRecordId, UUID prescriptionId, List<Map<String, Object>> items) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("medicalRecordId", medicalRecordId.toString());
    if (prescriptionId != null) {
      body.put("prescriptionId", prescriptionId.toString());
    }
    body.put("items", items);
    return objectMapper.writeValueAsString(body);
  }

  protected Map<String, Object> line(UUID chargeItemId, int quantity) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("chargeItemId", chargeItemId.toString());
    line.put("quantity", quantity);
    return line;
  }

  protected String createBill(User operator, String body, String idempotencyKey) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/billing/bills")
            .header(HttpHeaders.AUTHORIZATION, bearer(operator))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    return mockMvc
        .perform(request)
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  protected String payBill(User operator, UUID billId, String idempotencyKey) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/billing/bills/" + billId + "/pay")
            .header(HttpHeaders.AUTHORIZATION, bearer(operator));
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    return mockMvc
        .perform(request)
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  protected com.zjgsu.sfy.billing.entity.Bill billOf(UUID billId) {
    return billRepository.findById(billId).orElseThrow();
  }

  protected PaymentRecord paymentOf(UUID billId) {
    return paymentRecordRepository.findByBillId(billId).orElseThrow();
  }

  protected List<com.zjgsu.sfy.billing.entity.BillOperation> operationsOf(UUID billId) {
    return billOperationRepository.findByBillIdOrderByCreatedAtAscIdAsc(billId);
  }
}
