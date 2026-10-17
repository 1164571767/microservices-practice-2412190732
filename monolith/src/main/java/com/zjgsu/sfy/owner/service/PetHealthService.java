package com.zjgsu.sfy.owner.service;

import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.medical.MedicalRecordSummary;
import com.zjgsu.sfy.owner.entity.Allergy;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.owner.entity.Vaccination;
import com.zjgsu.sfy.owner.repository.AllergyRepository;
import com.zjgsu.sfy.owner.repository.VaccinationRepository;
import com.zjgsu.sfy.owner.service.command.CreateAllergyCommand;
import com.zjgsu.sfy.owner.service.command.CreateVaccinationCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 宠物健康档案用例：疫苗、过敏史（本人可写，员工可读），以及病历索引只读聚合。 */
@Service
public class PetHealthService {

  private final PetService petService;
  private final VaccinationRepository vaccinationRepository;
  private final AllergyRepository allergyRepository;
  private final MedicalApi medicalApi;

  public PetHealthService(
      PetService petService,
      VaccinationRepository vaccinationRepository,
      AllergyRepository allergyRepository,
      MedicalApi medicalApi) {
    this.petService = petService;
    this.vaccinationRepository = vaccinationRepository;
    this.allergyRepository = allergyRepository;
    this.medicalApi = medicalApi;
  }

  @Transactional
  public Vaccination addVaccination(
      CurrentUser requester, UUID petId, CreateVaccinationCommand command) {
    Pet pet = petService.requireOwnedPet(requester, petId);
    return vaccinationRepository.saveAndFlush(
        Vaccination.create(
            pet.getId(),
            command.vaccineName(),
            command.administeredOn(),
            command.nextDueOn(),
            command.note()));
  }

  @Transactional(readOnly = true)
  public List<Vaccination> listVaccinations(CurrentUser requester, UUID petId) {
    Pet pet = petService.requireReadablePet(requester, petId);
    return vaccinationRepository.findByPetIdOrderByAdministeredOnDesc(pet.getId());
  }

  @Transactional
  public Allergy addAllergy(CurrentUser requester, UUID petId, CreateAllergyCommand command) {
    Pet pet = petService.requireOwnedPet(requester, petId);
    return allergyRepository.saveAndFlush(
        Allergy.create(pet.getId(), command.allergen(), command.severity(), command.note()));
  }

  @Transactional(readOnly = true)
  public List<Allergy> listAllergies(CurrentUser requester, UUID petId) {
    Pet pet = petService.requireReadablePet(requester, petId);
    return allergyRepository.findByPetIdOrderByCreatedAtAsc(pet.getId());
  }

  @Transactional(readOnly = true)
  public List<MedicalRecordSummary> listMedicalRecords(CurrentUser requester, UUID petId) {
    Pet pet = petService.requireReadablePet(requester, petId);
    return medicalApi.findRecordsByPet(pet.getId());
  }
}
