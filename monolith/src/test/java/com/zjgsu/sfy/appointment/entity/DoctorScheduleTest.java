package com.zjgsu.sfy.appointment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoctorScheduleTest {

  private static final UUID DOCTOR = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-11-10T00:00:00Z");
  private static final Instant END = Instant.parse("2026-11-10T04:00:00Z");

  @Test
  void createAcceptsValidRange() {
    DoctorSchedule schedule = DoctorSchedule.create(DOCTOR, START, END, 30);

    assertThat(schedule.getDoctorId()).isEqualTo(DOCTOR);
    assertThat(schedule.getSlotMinutes()).isEqualTo(30);
  }

  @Test
  void createRejectsEndNotAfterStart() {
    assertThatThrownBy(() -> DoctorSchedule.create(DOCTOR, END, START, 30))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsOutOfRangeSlotMinutes() {
    assertThatThrownBy(() -> DoctorSchedule.create(DOCTOR, START, END, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DoctorSchedule.create(DOCTOR, START, END, 481))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void coversOnlyRangesInsideTheSchedule() {
    DoctorSchedule schedule = DoctorSchedule.create(DOCTOR, START, END, 30);

    assertThat(
            schedule.covers(
                Instant.parse("2026-11-10T01:00:00Z"), Instant.parse("2026-11-10T01:30:00Z")))
        .isTrue();
    assertThat(
            schedule.covers(
                Instant.parse("2026-11-10T03:30:00Z"), Instant.parse("2026-11-10T04:30:00Z")))
        .isFalse();
    assertThat(
            schedule.covers(
                Instant.parse("2026-11-09T23:30:00Z"), Instant.parse("2026-11-10T00:30:00Z")))
        .isFalse();
  }
}
