package com.zjgsu.sfy.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.appointment.QueueNumberCalled;
import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@RecordApplicationEvents
class QueueIntegrationTest extends AppointmentIntegrationTest {

  @Autowired ApplicationEvents events;
  @Autowired AppointmentService appointmentService;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void heldClaimLeavesAnotherWaitingPatientAvailable() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    CurrentUser requester = new CurrentUser(staff.getId(), staff.getUsername(), Set.of(Role.STAFF));
    Appointment first = seedAppointment(pet.getId(), doctor.getId());
    Appointment second =
        seedAppointment(
            pet.getId(),
            doctor.getId(),
            SLOT_START.plusSeconds(1800),
            SLOT_START.plusSeconds(3600));
    appointmentService.checkIn(requester, first.getId());
    appointmentService.checkIn(requester, second.getId());

    CountDownLatch claimed = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<UUID> held =
        pool.submit(
            () ->
                transaction.execute(
                    status -> {
                      UUID id = appointmentService.callNext(requester, doctor.getId()).getId();
                      claimed.countDown();
                      try {
                        if (!release.await(30, TimeUnit.SECONDS))
                          throw new IllegalStateException("claim release timed out");
                      } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                      }
                      return id;
                    }));
    try {
      assertThat(claimed.await(30, TimeUnit.SECONDS)).as("首个叫号事务已认领并持锁").isTrue();
      UUID other = appointmentService.callNext(requester, doctor.getId()).getId();
      release.countDown();
      UUID original = held.get(30, TimeUnit.SECONDS);
      assertThat(original).isNotEqualTo(other);
      assertThat(Set.of(original, other)).containsExactlyInAnyOrder(first.getId(), second.getId());
    } finally {
      release.countDown();
      pool.shutdownNow();
      pool.awaitTermination(30, TimeUnit.SECONDS);
    }
    assertThat(
            appointmentRepository.findByDoctorIdAndStatusOrderByCheckedInAtAsc(
                doctor.getId(), AppointmentStatus.CHECKED_IN))
        .isEmpty();
  }

  @Test
  void staffSeesCheckedInAppointmentsInQueue() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment first = seedAppointment(pet.getId(), doctor.getId());
    Appointment second =
        seedAppointment(
            pet.getId(),
            doctor.getId(),
            Instant.parse("2026-11-10T01:30:00Z"),
            Instant.parse("2026-11-10T02:00:00Z"));
    String token = bearer(staff);

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + first.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/appointment/appointments/" + second.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].position").value(1))
        .andExpect(jsonPath("$[1].position").value(2));
  }

  @Test
  void callNextTransitionsAndPublishesQueueNumberCalled() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String token = bearer(staff);
    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/appointment/queue/call")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(appointment.getId().toString()))
        .andExpect(jsonPath("$.status").value("CALLED"));

    assertThat(events.stream(QueueNumberCalled.class).count()).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void callingWithEmptyQueueIsRejected() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            post("/api/appointment/queue/call")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
  }

  /** M-2 回归：并发叫号必须各自认领不同患者（FOR UPDATE SKIP LOCKED），不能全部撞同一行。 */
  @Test
  void concurrentCallNextCallsDistinctPatients() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    CurrentUser requester = new CurrentUser(staff.getId(), staff.getUsername(), Set.of(Role.STAFF));

    int attempts = 4;
    Set<UUID> seeded = new HashSet<>();
    for (int i = 0; i < attempts; i++) {
      Appointment appointment =
          seedAppointment(
              pet.getId(),
              doctor.getId(),
              SLOT_START.plusSeconds(i * 1800L),
              SLOT_START.plusSeconds(i * 1800L + 1800));
      appointmentService.checkIn(requester, appointment.getId());
      seeded.add(appointment.getId());
    }

    ExecutorService pool = Executors.newFixedThreadPool(attempts);
    CyclicBarrier barrier = new CyclicBarrier(attempts);
    List<Future<UUID>> results = new ArrayList<>();
    try {
      for (int i = 0; i < attempts; i++) {
        results.add(
            pool.submit(
                () -> {
                  barrier.await(30, TimeUnit.SECONDS);
                  try {
                    return appointmentService.callNext(requester, doctor.getId()).getId();
                  } catch (BusinessException ex) {
                    return null;
                  }
                }));
      }
      Set<UUID> called = new HashSet<>();
      for (Future<UUID> result : results) {
        UUID id = result.get(30, TimeUnit.SECONDS);
        assertThat(id).as("并发叫号不应失败").isNotNull();
        called.add(id);
      }
      assertThat(called).hasSize(attempts).isSubsetOf(seeded);
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            appointmentRepository.findByDoctorIdAndStatusOrderByCheckedInAtAsc(
                doctor.getId(), AppointmentStatus.CHECKED_IN))
        .isEmpty();
  }

  @Test
  void ownerCannotViewQueue() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }
}
