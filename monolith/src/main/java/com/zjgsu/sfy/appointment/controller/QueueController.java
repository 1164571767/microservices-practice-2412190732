package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.dto.resp.AppointmentResponse;
import com.zjgsu.sfy.appointment.dto.resp.QueueEntryResponse;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.identity.IdentityApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointment/queue")
@PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
public class QueueController {

  private final AppointmentService appointmentService;
  private final IdentityApi identityApi;

  public QueueController(AppointmentService appointmentService, IdentityApi identityApi) {
    this.appointmentService = appointmentService;
    this.identityApi = identityApi;
  }

  @GetMapping
  public List<QueueEntryResponse> queue(@RequestParam UUID doctorId) {
    List<Appointment> waiting = appointmentService.queue(identityApi.currentUser(), doctorId);
    List<QueueEntryResponse> entries = new ArrayList<>();
    for (int i = 0; i < waiting.size(); i++) {
      entries.add(QueueEntryResponse.of(i + 1, waiting.get(i)));
    }
    return entries;
  }

  @PostMapping("/call")
  public AppointmentResponse callNext(@RequestParam UUID doctorId) {
    return AppointmentResponse.from(
        appointmentService.callNext(identityApi.currentUser(), doctorId));
  }
}
