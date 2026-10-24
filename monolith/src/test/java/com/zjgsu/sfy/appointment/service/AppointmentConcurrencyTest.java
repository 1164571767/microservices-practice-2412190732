package com.zjgsu.sfy.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AppointmentConcurrencyTest extends AppointmentIntegrationTest {

  private static final int ATTEMPTS = 6;

  @Autowired AppointmentService appointmentService;

  /** 同一医生同一时段被并发预约时，DB 唯一键 + 排班悲观锁兜底，只有一次成功。 */
  @Test
  void concurrentBookingOfSameSlotSucceedsExactlyOnce() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    CurrentUser requester = new CurrentUser(owner.getId(), owner.getUsername(), Set.of(Role.OWNER));
    BookAppointmentCommand command =
        new BookAppointmentCommand(pet.getId(), doctor.getId(), SLOT_START, SLOT_END);

    ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
    CyclicBarrier barrier = new CyclicBarrier(ATTEMPTS);
    List<Future<Boolean>> results = new ArrayList<>();
    try {
      for (int i = 0; i < ATTEMPTS; i++) {
        results.add(pool.submit(bookOnce(barrier, requester, command)));
      }
      int successes = 0;
      for (Future<Boolean> result : results) {
        if (result.get(30, TimeUnit.SECONDS)) {
          successes++;
        }
      }
      assertThat(successes).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * H-1 回归：并发预约「不同开始时间但区间重叠」时，排班锁内的重叠查询必须看到最新已提交数据。
   *
   * <p>MySQL REPEATABLE READ 下，事务前段的普通 SELECT 会固定快照，导致锁内重叠查询读旧数据而漏判； 本用例不依赖相同 startAt，因此生成列唯一键不会兜底。
   */
  @Test
  void concurrentOverlappingDifferentStartsNeverDoubleBook() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    CurrentUser requester = new CurrentUser(owner.getId(), owner.getUsername(), Set.of(Role.OWNER));
    ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
    CyclicBarrier barrier = new CyclicBarrier(ATTEMPTS);
    List<Future<Boolean>> results = new ArrayList<>();
    try {
      for (int i = 0; i < ATTEMPTS; i++) {
        Instant start = SLOT_START.plusSeconds(i * 300L);
        BookAppointmentCommand command =
            new BookAppointmentCommand(pet.getId(), doctor.getId(), start, start.plusSeconds(1800));
        results.add(pool.submit(bookOnce(barrier, requester, command)));
      }
      int successes = 0;
      for (Future<Boolean> result : results) {
        if (result.get(30, TimeUnit.SECONDS)) {
          successes++;
        }
      }
      assertThat(successes).isGreaterThanOrEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    List<Appointment> booked =
        appointmentRepository.findByDoctorIdAndStatusNotAndStartAtLessThanAndEndAtGreaterThan(
            doctor.getId(), AppointmentStatus.CANCELLED, SCHEDULE_END, SCHEDULE_START);
    assertThat(booked).isNotEmpty();
    for (int i = 0; i < booked.size(); i++) {
      for (int j = i + 1; j < booked.size(); j++) {
        assertThat(overlaps(booked.get(i), booked.get(j)))
            .as("预约区间不得重叠：%s 与 %s", booked.get(i).getId(), booked.get(j).getId())
            .isFalse();
      }
    }
  }

  /** M-1 回归：并发同幂等键重试必须回放同一预约，而不是在唯一键上失败。 */
  @Test
  void concurrentSameIdempotencyKeyReplaysSameAppointment() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    CurrentUser requester = new CurrentUser(owner.getId(), owner.getUsername(), Set.of(Role.OWNER));
    BookAppointmentCommand command =
        new BookAppointmentCommand(pet.getId(), doctor.getId(), SLOT_START, SLOT_END);
    String key = "concurrent-book-" + UUID.randomUUID();

    ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
    CyclicBarrier barrier = new CyclicBarrier(ATTEMPTS);
    List<Future<UUID>> results = new ArrayList<>();
    try {
      for (int i = 0; i < ATTEMPTS; i++) {
        results.add(
            pool.submit(
                () -> {
                  barrier.await(30, TimeUnit.SECONDS);
                  try {
                    return appointmentService.book(requester, command, key).getId();
                  } catch (BusinessException ex) {
                    return null;
                  }
                }));
      }
      Set<UUID> ids = new HashSet<>();
      for (Future<UUID> result : results) {
        UUID id = result.get(30, TimeUnit.SECONDS);
        assertThat(id).as("同键并发重试不应失败").isNotNull();
        ids.add(id);
      }
      assertThat(ids).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private static boolean overlaps(Appointment first, Appointment second) {
    return first.getStartAt().isBefore(second.getEndAt())
        && second.getStartAt().isBefore(first.getEndAt());
  }

  private Callable<Boolean> bookOnce(
      CyclicBarrier barrier, CurrentUser requester, BookAppointmentCommand command) {
    return () -> {
      barrier.await(30, TimeUnit.SECONDS);
      try {
        appointmentService.book(requester, command, null);
        return true;
      } catch (BusinessException ex) {
        return false;
      }
    };
  }
}
