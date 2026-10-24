package com.zjgsu.sfy.medical.controller;

import static org.assertj.core.api.Assertions.*;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.medical.repository.ConsultationRepository;
import com.zjgsu.sfy.medical.service.ConsultationService;
import com.zjgsu.sfy.medical.service.command.ConsultationDraft;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConsultationConcurrencyTest extends MedicalIntegrationTest {
  @Autowired ConsultationService service;
  @Autowired ConsultationRepository consultations;
  @Autowired com.zjgsu.sfy.medical.service.MedicalRecordService recordWriter;
  @Autowired com.zjgsu.sfy.medical.service.PrescriptionService prescriptionWriter;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.zjgsu.sfy.appointment.service.AppointmentLookupAdapter appointmentLookup;

  @Test
  void legacyPrescriptionWaitingForLockMustSeeConcurrentCompletion() throws Exception {
    var doctor = seedDoctor();
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var a = seedAppointment(pet.getId(), doctor.getId());
    var actor = new CurrentUser(doctor.getId(), doctor.getUsername(), Set.of(Role.DOCTOR));
    var record =
        recordWriter.createRecord(
            actor,
            new com.zjgsu.sfy.medical.service.command.CreateMedicalRecordCommand(
                a.getId(), "症状", "诊断", null, null));
    var attemptedLock = new CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              attemptedLock.countDown();
              return invocation.callRealMethod();
            })
        .when(appointmentLookup)
        .lockById(a.getId());
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var pending =
          tx.execute(
              status -> {
                appointmentRepository.findByIdForUpdate(a.getId()).orElseThrow();
                var future =
                    pool.submit(
                        () ->
                            outcome(
                                () ->
                                    prescriptionWriter.issue(
                                        actor,
                                        record.getId(),
                                        new com.zjgsu.sfy.medical.service.command
                                            .IssuePrescriptionCommand(
                                            List.of(
                                                new com.zjgsu.sfy.medical.service.command
                                                    .PrescriptionLineCommand(
                                                    "药品", "规格", "1片", "每日一次", 1, 1, null))))));
                try {
                  assertThat(attemptedLock.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(ex);
                }
                service.complete(actor, a.getId(), 0, true);
                return future;
              });
      assertThat(pending.get(20, TimeUnit.SECONDS)).isEqualTo("OPERATION_NOT_ALLOWED");
    }
    assertThat(prescriptionRepository.existsByMedicalRecordId(record.getId())).isFalse();
    assertThat(consultations.get(a.getId()).completedAt()).isNotNull();
  }

  @Test
  void concurrentFirstSavesCannotOverwriteEachOther() throws Exception {
    var doctor = seedDoctor();
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var a = seedAppointment(pet.getId(), doctor.getId());
    var actor = new CurrentUser(doctor.getId(), doctor.getUsername(), Set.of(Role.DOCTOR));
    var draft = new ConsultationDraft("症状", "诊断", "", ConsultationDraft.Choice.NONE, List.of());
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return outcome(() -> service.saveDraft(actor, a.getId(), 0, draft));
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return outcome(() -> service.saveDraft(actor, a.getId(), 0, draft));
              });
      gate.countDown();
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "CONCURRENT_MODIFICATION");
    }
    assertThat(consultations.get(a.getId()).version()).isEqualTo(1);
    assertThat(consultations.operations(a.getId(), 0)).hasSize(1);
  }

  @Test
  void concurrentFormalSubmitsProduceOneRecordAndOneAudit() throws Exception {
    var doctor = seedDoctor();
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var a = seedAppointment(pet.getId(), doctor.getId());
    var actor = new CurrentUser(doctor.getId(), doctor.getUsername(), Set.of(Role.DOCTOR));
    service.saveDraft(
        actor,
        a.getId(),
        0,
        new ConsultationDraft("症状", "诊断", "", ConsultationDraft.Choice.NONE, List.of()));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return outcome(() -> service.submit(actor, a.getId(), 1));
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return outcome(() -> service.submit(actor, a.getId(), 1));
              });
      gate.countDown();
      // A winning NONE submission also closes the legacy prescription write path.
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "CONCURRENT_MODIFICATION");
    }
    assertThat(consultations.get(a.getId()).version()).isEqualTo(2);
    assertThat(consultations.operations(a.getId(), 0)).hasSize(2);
    assertThat(medicalRecordRepository.findByAppointmentId(a.getId())).isPresent();
  }

  private static String outcome(Runnable action) {
    try {
      action.run();
      return "OK";
    } catch (BusinessException ex) {
      return ex.errorCode().name();
    }
  }
}
