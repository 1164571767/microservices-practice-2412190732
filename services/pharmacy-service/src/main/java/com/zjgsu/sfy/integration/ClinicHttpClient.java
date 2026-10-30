package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.time.Instant;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ClinicHttpClient implements MedicalApi {
  private final HttpTransport http;
  private final PeerProperties peers;

  public ClinicHttpClient(HttpTransport http, PeerProperties peers) {
    this.http = http;
    this.peers = peers;
  }

  public Map<UUID, ConsultationSummary> consultationSummaries(Collection<UUID> ids) {
    return http.required(
        http.post(
            peers.getClinicUrl() + "/internal/clinic/consultations/batch",
            ids,
            new ParameterizedTypeReference<Map<UUID, ConsultationSummary>>() {}));
  }

  public Optional<PharmacyPrescription> pharmacyPrescription(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getClinicUrl() + "/internal/clinic/prescriptions/" + id + "/review",
            new ParameterizedTypeReference<PharmacyPrescription>() {}));
  }

  public Optional<PrescriptionSummary> findPrescription(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getClinicUrl() + "/internal/clinic/prescriptions/" + id,
            new ParameterizedTypeReference<PrescriptionSummary>() {}));
  }

  public Optional<MedicalRecordSummary> findRecord(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getClinicUrl() + "/internal/clinic/records/" + id,
            new ParameterizedTypeReference<MedicalRecordSummary>() {}));
  }

  public List<MedicalRecordSummary> findRecordsByPet(UUID id) {
    return http.required(
        http.get(
            peers.getClinicUrl() + "/internal/clinic/pets/" + id + "/records",
            new ParameterizedTypeReference<List<MedicalRecordSummary>>() {}));
  }

  public List<MedicalRecordSummary> findRecordsVisitedBetween(Instant from, Instant to) {
    String url =
        UriComponentsBuilder.fromUriString(peers.getClinicUrl() + "/internal/clinic/records")
            .queryParam("from", from)
            .queryParam("to", to)
            .build()
            .encode()
            .toUriString();
    return http.required(
        http.get(url, new ParameterizedTypeReference<List<MedicalRecordSummary>>() {}));
  }

  public Page<BillingRecordSummary> billingRecords(UUID petId, Pageable pageable) {
    String url =
        UriComponentsBuilder.fromUriString(
                peers.getClinicUrl() + "/internal/clinic/billing-records")
            .queryParam("petId", petId)
            .queryParam("page", pageable.getPageNumber())
            .queryParam("size", pageable.getPageSize())
            .build()
            .encode()
            .toUriString();
    return http.required(
            http.get(url, new ParameterizedTypeReference<PageEnvelope<BillingRecordSummary>>() {}))
        .page(pageable);
  }
}
