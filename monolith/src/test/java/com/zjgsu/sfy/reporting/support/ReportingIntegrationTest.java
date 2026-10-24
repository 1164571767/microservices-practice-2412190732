package com.zjgsu.sfy.reporting.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.repository.ChargeItemRepository;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import com.zjgsu.sfy.reporting.dto.resp.DrugConsumptionResponse;
import com.zjgsu.sfy.reporting.dto.resp.RevenueReportResponse;
import com.zjgsu.sfy.reporting.dto.resp.VisitReportResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** reporting 模块集成测试基类：复用 identity / owner / appointment / medical / pharmacy / billing 夹具。 */
public abstract class ReportingIntegrationTest extends PharmacyIntegrationTest {

  protected static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  protected static final MediaType JSON = MediaType.APPLICATION_JSON;

  @Autowired protected ChargeItemRepository chargeItemRepository;

  protected User seedAdmin() {
    return seedUser(Set.of(Role.ADMIN));
  }

  protected User seedStaff() {
    return seedUser(Set.of(Role.STAFF));
  }

  protected Pet seedPetWithOwner() {
    return seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
  }

  protected MedicalRecord seedRecordFor(Pet pet, User doctor) {
    return seedRecord(pet.getId(), doctor.getId(), Instant.now().minusSeconds(60), "诊断");
  }

  protected LocalDate today() {
    return LocalDate.now(ZONE);
  }

  protected ChargeItem seedChargeItem(String amount) {
    return chargeItemRepository.saveAndFlush(
        ChargeItem.create("收费项" + shortId(), new BigDecimal(amount)));
  }

  protected UUID createBillFor(User operator, UUID medicalRecordId, ChargeItem item, int quantity)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("medicalRecordId", medicalRecordId.toString());
    body.put(
        "items", List.of(Map.of("chargeItemId", item.getId().toString(), "quantity", quantity)));
    String response =
        mockMvc
            .perform(
                post("/api/billing/bills")
                    .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                    .contentType(JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  protected void payBill(User operator, UUID billId) throws Exception {
    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
        .andExpect(status().isCreated());
  }

  protected void refundBill(User operator, UUID billId, String reason) throws Exception {
    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                .contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))))
        .andExpect(status().isOk());
  }

  protected String dispense(User pharmacist, UUID prescriptionId) throws Exception {
    return mockMvc
        .perform(
            post("/api/pharmacy/prescriptions/" + prescriptionId + "/dispense")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  protected ResultActions visitReport(User caller, LocalDate from, LocalDate to, UUID doctorId)
      throws Exception {
    String path = "/api/reporting/visits?from=" + from + "&to=" + to;
    if (doctorId != null) {
      path += "&doctorId=" + doctorId;
    }
    return mockMvc
        .perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(caller)))
        .andExpect(status().isOk());
  }

  protected VisitReportResponse visitsOf(User caller, LocalDate from, LocalDate to, UUID doctorId)
      throws Exception {
    String body =
        visitReport(caller, from, to, doctorId).andReturn().getResponse().getContentAsString();
    return objectMapper.readValue(body, VisitReportResponse.class);
  }

  protected RevenueReportResponse revenueOf(User caller, LocalDate from, LocalDate to)
      throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/reporting/revenue?from=" + from + "&to=" + to)
                    .header(HttpHeaders.AUTHORIZATION, bearer(caller)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, RevenueReportResponse.class);
  }

  protected DrugConsumptionResponse drugConsumptionOf(
      User caller, LocalDate from, LocalDate to, UUID drugId) throws Exception {
    String path = "/api/reporting/drug-consumption?from=" + from + "&to=" + to;
    if (drugId != null) {
      path += "&drugId=" + drugId;
    }
    String body =
        mockMvc
            .perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(caller)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, DrugConsumptionResponse.class);
  }

  private static String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
  }
}
