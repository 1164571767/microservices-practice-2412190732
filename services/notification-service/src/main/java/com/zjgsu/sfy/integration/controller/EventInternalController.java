package com.zjgsu.sfy.integration.controller;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventInternalController {
  private final ApplicationEventPublisher events;

  public EventInternalController(ApplicationEventPublisher events) {
    this.events = events;
  }

  @PostMapping("/internal/events/AppointmentBooked")
  public void onAppointmentBooked(@RequestBody com.zjgsu.sfy.appointment.AppointmentBooked event) {
    events.publishEvent(event);
  }

  @PostMapping("/internal/events/QueueNumberCalled")
  public void onQueueNumberCalled(@RequestBody com.zjgsu.sfy.appointment.QueueNumberCalled event) {
    events.publishEvent(event);
  }

  @PostMapping("/internal/events/BillPaid")
  public void onBillPaid(@RequestBody com.zjgsu.sfy.billing.BillPaid event) {
    events.publishEvent(event);
  }

  @PostMapping("/internal/events/LowStockDetected")
  public void onLowStockDetected(@RequestBody com.zjgsu.sfy.pharmacy.LowStockDetected event) {
    events.publishEvent(event);
  }
}
