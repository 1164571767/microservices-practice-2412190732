package com.zjgsu.sfy.appointment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentTest {

  private static final UUID PET = UUID.randomUUID();
  private static final UUID DOCTOR = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-11-10T01:00:00Z");
  private static final Instant END = Instant.parse("2026-11-10T01:30:00Z");

  private static Appointment newAppointment() {
    return Appointment.book(PET, DOCTOR, START, END);
  }

  @Test
  void bookingStartsScheduled() {
    Appointment appointment = newAppointment();

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    assertThat(appointment.getCheckedInAt()).isNull();
    assertThat(appointment.getCalledAt()).isNull();
  }

  @Test
  void bookingRejectsNonPositiveRange() {
    assertThatThrownBy(() -> Appointment.book(PET, DOCTOR, END, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checkInMovesToCheckedInFromScheduled() {
    Appointment appointment = newAppointment();

    appointment.checkIn();

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
    assertThat(appointment.getCheckedInAt()).isNotNull();
  }

  @Test
  void checkInTwiceIsRejected() {
    Appointment appointment = newAppointment();
    appointment.checkIn();

    assertThatThrownBy(appointment::checkIn).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void callRequiresCheckedIn() {
    Appointment appointment = newAppointment();

    assertThatThrownBy(appointment::call).isInstanceOf(IllegalStateException.class);

    appointment.checkIn();
    appointment.call();

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CALLED);
    assertThat(appointment.getCalledAt()).isNotNull();
  }

  @Test
  void cancelAllowedFromScheduledAndCheckedIn() {
    Appointment scheduled = newAppointment();
    scheduled.cancel();
    assertThat(scheduled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

    Appointment checkedIn = newAppointment();
    checkedIn.checkIn();
    checkedIn.cancel();
    assertThat(checkedIn.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
  }

  @Test
  void cancelTwiceIsRejected() {
    Appointment appointment = newAppointment();
    appointment.cancel();

    assertThatThrownBy(appointment::cancel).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rescheduleOnlyAllowedFromScheduled() {
    Appointment appointment = newAppointment();
    Instant newStart = Instant.parse("2026-11-10T02:00:00Z");
    Instant newEnd = Instant.parse("2026-11-10T02:30:00Z");
    appointment.reschedule(newStart, newEnd);
    assertThat(appointment.getStartAt()).isEqualTo(newStart);
    assertThat(appointment.getEndAt()).isEqualTo(newEnd);

    appointment.cancel();
    assertThatThrownBy(() -> appointment.reschedule(newStart, newEnd))
        .isInstanceOf(IllegalStateException.class);
  }
}
