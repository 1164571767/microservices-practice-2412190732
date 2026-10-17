package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.dto.req.BookAppointmentRequest;
import com.zjgsu.sfy.appointment.dto.req.RescheduleRequest;
import com.zjgsu.sfy.appointment.dto.resp.AppointmentResponse;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.identity.IdentityApi;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointment")
public class AppointmentController {

  private final AppointmentService appointmentService;
  private final IdentityApi identityApi;

  public AppointmentController(AppointmentService appointmentService, IdentityApi identityApi) {
    this.appointmentService = appointmentService;
    this.identityApi = identityApi;
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  public AppointmentResponse book(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody BookAppointmentRequest request) {
    return AppointmentResponse.from(
        appointmentService.book(identityApi.currentUser(), request.toCommand(), idempotencyKey));
  }

  @GetMapping("/appointments/{appointmentId}")
  public AppointmentResponse getAppointment(@PathVariable UUID appointmentId) {
    return AppointmentResponse.from(
        appointmentService.getAppointment(identityApi.currentUser(), appointmentId));
  }

  @GetMapping("/pets/{petId}/appointments")
  public List<AppointmentResponse> listByPet(@PathVariable UUID petId) {
    return appointmentService.listByPet(identityApi.currentUser(), petId).stream()
        .map(AppointmentResponse::from)
        .toList();
  }

  @PostMapping("/appointments/{appointmentId}/cancel")
  public AppointmentResponse cancel(@PathVariable UUID appointmentId) {
    return AppointmentResponse.from(
        appointmentService.cancel(identityApi.currentUser(), appointmentId));
  }

  @PostMapping("/appointments/{appointmentId}/reschedule")
  public AppointmentResponse reschedule(
      @PathVariable UUID appointmentId, @Valid @RequestBody RescheduleRequest request) {
    return AppointmentResponse.from(
        appointmentService.reschedule(
            identityApi.currentUser(), appointmentId, request.toCommand()));
  }

  @PostMapping("/appointments/{appointmentId}/check-in")
  @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
  public AppointmentResponse checkIn(@PathVariable UUID appointmentId) {
    return AppointmentResponse.from(
        appointmentService.checkIn(identityApi.currentUser(), appointmentId));
  }
}
