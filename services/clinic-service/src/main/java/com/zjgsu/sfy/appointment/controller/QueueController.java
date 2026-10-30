package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.dto.resp.AppointmentResponse;
import com.zjgsu.sfy.appointment.dto.resp.QueueEntryResponse;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "候诊队列", description = "医生叫号与候诊队列；前台、医生、管理员可用")
public class QueueController {

  private final AppointmentService appointmentService;
  private final IdentityApi identityApi;
  private final com.zjgsu.sfy.appointment.service.ReceptionQueryService reception;

  public QueueController(
      AppointmentService appointmentService,
      IdentityApi identityApi,
      com.zjgsu.sfy.appointment.service.ReceptionQueryService reception) {
    this.appointmentService = appointmentService;
    this.identityApi = identityApi;
    this.reception = reception;
  }

  @GetMapping
  @Operation(summary = "候诊列表", description = "前台、医生或管理员查看某医生当前的候诊队列，按签到先后编号，1 号为下一位。")
  public List<QueueEntryResponse> queue(
      @Parameter(description = "医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
          @RequestParam
          UUID doctorId) {
    List<Appointment> waiting = appointmentService.queue(identityApi.currentUser(), doctorId);
    var contacts = reception.contacts(waiting);
    List<QueueEntryResponse> entries = new ArrayList<>();
    for (int i = 0; i < waiting.size(); i++) {
      entries.add(
          QueueEntryResponse.of(i + 1, waiting.get(i), contacts.get(waiting.get(i).getPetId())));
    }
    return entries;
  }

  @PostMapping("/call")
  @Operation(
      summary = "叫下一号",
      description = "叫队列中最早签到的一位。队列为空时返回 OPERATION_NOT_ALLOWED，叫号后预约状态变为 CALLED。")
  public AppointmentResponse callNext(
      @Parameter(description = "医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
          @RequestParam
          UUID doctorId) {
    return AppointmentResponse.from(
        appointmentService.callNext(identityApi.currentUser(), doctorId));
  }
}
