package com.zjgsu.sfy.integration.controller;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventInternalController {
  private final ApplicationEventPublisher events;

  public EventInternalController(ApplicationEventPublisher events) {
    this.events = events;
  }

  @PostMapping("/internal/events/MedicalRecordCreated")
  public void onMedicalRecordCreated(
      @RequestBody com.zjgsu.sfy.medical.MedicalRecordCreated event) {
    events.publishEvent(event);
  }

  @PostMapping("/internal/events/BillPaid")
  public void onBillPaid(@RequestBody com.zjgsu.sfy.billing.BillPaid event) {
    events.publishEvent(event);
  }

  @PostMapping("/internal/events/BillRefunded")
  public void onBillRefunded(@RequestBody com.zjgsu.sfy.billing.BillRefunded event) {
    events.publishEvent(event);
  }

  @PostMapping("/internal/events/PrescriptionDispensed")
  public void onPrescriptionDispensed(
      @RequestBody com.zjgsu.sfy.pharmacy.PrescriptionDispensed event) {
    events.publishEvent(event);
  }
}
