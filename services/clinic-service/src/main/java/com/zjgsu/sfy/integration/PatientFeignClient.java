package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.owner.PatientContactSummary;
import com.zjgsu.sfy.owner.PetSummary;
import com.zjgsu.sfy.owner.PharmacyAllergy;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "patient-service", configuration = PatientFeignClient.Settings.class)
public interface PatientFeignClient {
  @GetMapping("/internal/patients/pets/{id}")
  PetSummary findPet(@PathVariable("id") UUID id);

  @PostMapping("/internal/patients/pets/{id}/admission")
  void requireActivePetForBooking(@PathVariable("id") UUID id);

  @GetMapping("/internal/patients/owners/by-user/{id}")
  UUID findOwnerIdByUser(@PathVariable("id") UUID id);

  @GetMapping("/internal/patients/pets/{id}/user")
  UUID findUserIdByPet(@PathVariable("id") UUID id);

  @GetMapping("/internal/patients/pets/{id}/allergies")
  List<PharmacyAllergy> pharmacyAllergies(@PathVariable("id") UUID id);

  @GetMapping("/internal/patients/search")
  List<UUID> searchPatientIds(@RequestParam("query") String query);

  @PostMapping("/internal/patients/contacts")
  Map<UUID, PatientContactSummary> patientContacts(@RequestBody Collection<UUID> ids);

  class Settings {
    @Bean
    RequestInterceptor patientServiceToken(@Value("${services.token}") String token) {
      return request -> request.header("X-Service-Token", token);
    }

    @Bean
    Request.Options patientTimeouts(
        @Value("${services.connect-timeout}") String connect,
        @Value("${services.read-timeout}") String read) {
      return new Request.Options(
          DurationStyle.detectAndParse(connect).toMillis(),
          TimeUnit.MILLISECONDS,
          DurationStyle.detectAndParse(read).toMillis(),
          TimeUnit.MILLISECONDS,
          false);
    }

    @Bean
    Retryer patientRetryer() {
      return Retryer.NEVER_RETRY;
    }
  }
}
