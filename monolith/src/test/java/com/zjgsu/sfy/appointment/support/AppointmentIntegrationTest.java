package com.zjgsu.sfy.appointment.support;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.appointment.repository.DoctorScheduleRepository;
import com.zjgsu.sfy.owner.support.OwnerIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** appointment 模块集成测试基类：复用 identity/owner 的注册登录、真实 MySQL 与宠物夹具。 */
public abstract class AppointmentIntegrationTest extends OwnerIntegrationTest {

  protected static final Instant SCHEDULE_START = Instant.parse("2026-11-10T00:00:00Z");
  protected static final Instant SCHEDULE_END = Instant.parse("2026-11-10T04:00:00Z");
  protected static final Instant SLOT_START = Instant.parse("2026-11-10T01:00:00Z");
  protected static final Instant SLOT_END = Instant.parse("2026-11-10T01:30:00Z");
  protected static final LocalDate SLOT_DATE = LocalDate.of(2026, 11, 10);

  @Autowired protected DoctorScheduleRepository scheduleRepository;
  @Autowired protected AppointmentRepository appointmentRepository;

  protected DoctorSchedule seedSchedule(UUID doctorId) {
    return scheduleRepository.saveAndFlush(
        DoctorSchedule.create(doctorId, SCHEDULE_START, SCHEDULE_END, 30));
  }

  protected Appointment seedAppointment(UUID petId, UUID doctorId) {
    return seedAppointment(petId, doctorId, SLOT_START, SLOT_END);
  }

  protected Appointment seedAppointment(UUID petId, UUID doctorId, Instant startAt, Instant endAt) {
    return appointmentRepository.saveAndFlush(Appointment.book(petId, doctorId, startAt, endAt));
  }

  protected String scheduleBody(UUID doctorId, Instant startAt, Instant endAt, int slotMinutes) {
    return objectMapper.writeValueAsString(
        Map.of(
            "doctorId", doctorId.toString(),
            "startAt", startAt.toString(),
            "endAt", endAt.toString(),
            "slotMinutes", slotMinutes));
  }

  protected String bookBody(UUID petId, UUID doctorId, Instant startAt, Instant endAt) {
    return objectMapper.writeValueAsString(
        Map.of(
            "petId", petId.toString(),
            "doctorId", doctorId.toString(),
            "startAt", startAt.toString(),
            "endAt", endAt.toString()));
  }

  protected String rescheduleBody(Instant startAt, Instant endAt) {
    return objectMapper.writeValueAsString(
        Map.of("startAt", startAt.toString(), "endAt", endAt.toString()));
  }
}
