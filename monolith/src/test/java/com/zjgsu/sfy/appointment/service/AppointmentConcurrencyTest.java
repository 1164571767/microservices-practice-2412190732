package com.zjgsu.sfy.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Pet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
