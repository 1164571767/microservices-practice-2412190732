package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.integration.*;
import com.zjgsu.sfy.medical.PrescriptionIssued;
import com.zjgsu.sfy.pharmacy.repository.PrescriptionTaskRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PrescriptionTaskListener {
  private final HttpTransport http;
  private final PeerProperties peers;
  private final PrescriptionTaskRepository tasks;
  private final TransactionTemplate transactions;

  public PrescriptionTaskListener(
      HttpTransport http,
      PeerProperties peers,
      PrescriptionTaskRepository tasks,
      PlatformTransactionManager manager) {
    this.http = http;
    this.peers = peers;
    this.tasks = tasks;
    this.transactions = new TransactionTemplate(manager);
  }

  public void refresh() {
    // Full paginated reconciliation: source prescriptions are immutable; repeated reads are
    // harmless.
    // Each HTTP request runs before its local transaction. Nothing advances a durable cursor on
    // failure.
    for (int page = 0; ; page++) {
      var batch =
          http.required(
              http.get(
                  peers.getClinicUrl()
                      + "/internal/clinic/prescription-index?page="
                      + page
                      + "&size=100",
                  new ParameterizedTypeReference<PageEnvelope<PrescriptionIssued>>() {}));
      transactions.executeWithoutResult(
          status -> {
            for (var task : batch.content())
              tasks.upsert(task.prescriptionId(), task.petId(), task.doctorId(), task.issuedAt());
          });
      if (batch.content().size() < 100) return;
    }
  }
}
