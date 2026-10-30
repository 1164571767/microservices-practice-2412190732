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

/** 宠物健康资料：本人或临床员工可追加，临床员工可留痕更正；归档后只读。 */
@Service
public class PetHealthService {

  private final PetService petService;
  private final VaccinationRepository vaccinationRepository;
  private final AllergyRepository allergyRepository;
  private final MedicalApi medicalApi;
  private final ClinicArchiveService archives;

  public PetHealthService(
      PetService petService,
      VaccinationRepository vaccinationRepository,
      AllergyRepository allergyRepository,
      MedicalApi medicalApi,
      ClinicArchiveService archives) {
    this.petService = petService;
    this.vaccinationRepository = vaccinationRepository;
    this.allergyRepository = allergyRepository;
    this.medicalApi = medicalApi;
    this.archives = archives;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Vaccination addVaccination(
      CurrentUser requester, UUID petId, CreateVaccinationCommand command) {
    Pet pet = writablePet(requester, petId);
    Vaccination record =
        Vaccination.create(
            pet.getId(),
            command.vaccineName(),
            command.administeredOn(),
            command.nextDueOn(),
            command.note());
    record.recordActor(requester.userId(), requester.username());
    return vaccinationRepository.saveAndFlush(record);
  }

  @Transactional(readOnly = true)
  public List<Vaccination> listVaccinations(CurrentUser requester, UUID petId) {
    Pet pet = petService.requireReadablePet(requester, petId);
    return vaccinationRepository.findByPetIdOrderByAdministeredOnDesc(pet.getId());
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Allergy addAllergy(CurrentUser requester, UUID petId, CreateAllergyCommand command) {
    Pet pet = writablePet(requester, petId);
    Allergy record =
        Allergy.create(pet.getId(), command.allergen(), command.severity(), command.note());
    record.recordActor(requester.userId(), requester.username());
    return allergyRepository.saveAndFlush(record);
  }

  @Transactional(readOnly = true)
  public List<Allergy> listAllergies(CurrentUser requester, UUID petId) {
    Pet pet = petService.requireReadablePet(requester, petId);
    return allergyRepository.findByPetIdOrderByCreatedAtAsc(pet.getId());
  }

  private Pet writablePet(CurrentUser user, UUID id) {
    return user.isClinicStaff() ? archives.lockActivePet(id) : petService.requireOwnedPet(user, id);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Vaccination correctVaccination(
      CurrentUser user, UUID petId, UUID recordId, CreateVaccinationCommand c, String reason) {
    ClinicArchiveService.requireStaff(user);
    archives.lockActivePet(petId);
    Vaccination old =
        vaccinationRepository
            .findById(recordId)
            .orElseThrow(
                () ->
                    new com.zjgsu.sfy.common.error.BusinessException(
                        com.zjgsu.sfy.common.error.ErrorCode.RESOURCE_NOT_FOUND));
    if (!old.getPetId().equals(petId))
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.RESOURCE_NOT_FOUND);
    if (vaccinationRepository.existsBySupersedesId(recordId))
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.CONCURRENT_MODIFICATION, "记录已有更正，请刷新后核对");
    Vaccination r =
        Vaccination.create(petId, c.vaccineName(), c.administeredOn(), c.nextDueOn(), c.note());
    r.recordActor(user.userId(), user.username());
    r.corrects(recordId, reason);
    return vaccinationRepository.saveAndFlush(r);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Allergy correctAllergy(
      CurrentUser user, UUID petId, UUID recordId, CreateAllergyCommand c, String reason) {
    ClinicArchiveService.requireStaff(user);
    archives.lockActivePet(petId);
    Allergy old =
        allergyRepository
            .findById(recordId)
            .orElseThrow(
                () ->
                    new com.zjgsu.sfy.common.error.BusinessException(
                        com.zjgsu.sfy.common.error.ErrorCode.RESOURCE_NOT_FOUND));
    if (!old.getPetId().equals(petId))
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.RESOURCE_NOT_FOUND);
    if (allergyRepository.existsBySupersedesId(recordId))
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.CONCURRENT_MODIFICATION, "记录已有更正，请刷新后核对");
    Allergy r = Allergy.create(petId, c.allergen(), c.severity(), c.note());
    r.recordActor(user.userId(), user.username());
    r.corrects(recordId, reason);
    return allergyRepository.saveAndFlush(r);
  }

  @Transactional(readOnly = true)
  public List<MedicalRecordSummary> listMedicalRecords(CurrentUser requester, UUID petId) {
    Pet pet = petService.requireReadablePet(requester, petId);
    return medicalApi.findRecordsByPet(pet.getId());
  }
}
