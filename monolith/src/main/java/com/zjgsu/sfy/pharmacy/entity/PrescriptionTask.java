package com.zjgsu.sfy.pharmacy.entity;

import com.zjgsu.sfy.medical.PrescriptionIssued;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pharmacy_prescription_task")
public class PrescriptionTask {
  @Id
  @Column(name = "prescription_id")
  private UUID prescriptionId;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  protected PrescriptionTask() {}

  public static PrescriptionTask from(PrescriptionIssued event) {
    var task = new PrescriptionTask();
    task.prescriptionId = event.prescriptionId();
    task.petId = event.petId();
    task.doctorId = event.doctorId();
    task.issuedAt = event.issuedAt();
    return task;
  }

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public UUID getPetId() {
    return petId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }
}
