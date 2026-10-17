package com.zjgsu.sfy.appointment.service;

import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.medical.AppointmentLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 实现 medical 的 {@link AppointmentLookup} 端口，向 medical 提供接诊医生与宠物归属。 依赖方向 appointment → medical，避免
 * medical 反向依赖 appointment 造成循环（见 ADR-0006）。
 */
@Component
public class AppointmentLookupAdapter implements AppointmentLookup {

  private final AppointmentRepository appointmentRepository;

  public AppointmentLookupAdapter(AppointmentRepository appointmentRepository) {
    this.appointmentRepository = appointmentRepository;
  }

  @Override
  public Optional<AppointmentInfo> findById(UUID appointmentId) {
    return appointmentRepository
        .findById(appointmentId)
        .map(
            appointment ->
                new AppointmentInfo(
                    appointment.getId(), appointment.getPetId(), appointment.getDoctorId()));
  }
}
