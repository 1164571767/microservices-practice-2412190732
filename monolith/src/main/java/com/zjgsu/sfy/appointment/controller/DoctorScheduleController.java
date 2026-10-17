package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.dto.req.CreateScheduleRequest;
import com.zjgsu.sfy.appointment.dto.resp.ScheduleResponse;
import com.zjgsu.sfy.appointment.dto.resp.TimeSlotResponse;
import com.zjgsu.sfy.appointment.service.DoctorScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointment")
public class DoctorScheduleController {

  private final DoctorScheduleService scheduleService;

  public DoctorScheduleController(DoctorScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @PostMapping("/schedules")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public ScheduleResponse createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
    return ScheduleResponse.from(scheduleService.createSchedule(request.toCommand()));
  }

  @GetMapping("/schedules")
  public List<ScheduleResponse> listSchedules(@RequestParam UUID doctorId) {
    return scheduleService.listSchedules(doctorId).stream().map(ScheduleResponse::from).toList();
  }

  @DeleteMapping("/schedules/{scheduleId}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSchedule(@PathVariable UUID scheduleId) {
    scheduleService.deleteSchedule(scheduleId);
  }

  @GetMapping("/availability")
  public List<TimeSlotResponse> availability(
      @RequestParam UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return scheduleService.availability(doctorId, date).stream()
        .map(TimeSlotResponse::from)
        .toList();
  }
}
