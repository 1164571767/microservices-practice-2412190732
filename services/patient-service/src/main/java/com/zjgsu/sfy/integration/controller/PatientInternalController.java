package com.zjgsu.sfy.integration.controller;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.integration.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PatientInternalController {

  private final OwnerApi api;

  public PatientInternalController(OwnerApi api) {
    this.api = api;
  }

  @GetMapping("/internal/patients/pets/{id}")
  public PetSummary pet(@PathVariable UUID id) {
    return api.findPet(id).orElse(null);
  }

  @PostMapping("/internal/patients/pets/{id}/admission")
  public void admission(@PathVariable UUID id) {
    api.requireActivePetForBooking(id);
  }

  @GetMapping("/internal/patients/owners/by-user/{id}")
  public UUID owner(@PathVariable UUID id) {
    return api.findOwnerIdByUser(id).orElse(null);
  }

  @GetMapping("/internal/patients/pets/{id}/user")
  public UUID user(@PathVariable UUID id) {
    return api.findUserIdByPet(id).orElse(null);
  }

  @GetMapping("/internal/patients/pets/{id}/allergies")
  public List<PharmacyAllergy> allergies(@PathVariable UUID id) {
    return api.pharmacyAllergies(id);
  }

  @GetMapping("/internal/patients/search")
  public List<UUID> search(@RequestParam String query) {
    return api.searchPatientIds(query);
  }

  @PostMapping("/internal/patients/contacts")
  public Map<UUID, PatientContactSummary> contacts(@RequestBody Collection<UUID> ids) {
    return api.patientContacts(ids);
  }
}
