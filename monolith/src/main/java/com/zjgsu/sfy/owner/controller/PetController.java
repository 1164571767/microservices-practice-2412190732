package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.UpsertPetRequest;
import com.zjgsu.sfy.owner.dto.resp.PageResponse;
import com.zjgsu.sfy.owner.dto.resp.PetResponse;
import com.zjgsu.sfy.owner.service.PetService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/pets")
public class PetController {

  private final PetService petService;
  private final IdentityApi identityApi;

  public PetController(PetService petService, IdentityApi identityApi) {
    this.petService = petService;
    this.identityApi = identityApi;
  }

  @GetMapping
  public PageResponse<PetResponse> listPets(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.of(
        petService.listPets(identityApi.currentUser(), pageable), PetResponse::from);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PetResponse createPet(@Valid @RequestBody UpsertPetRequest request) {
    return PetResponse.from(petService.createPet(identityApi.currentUser(), request.toCommand()));
  }

  @GetMapping("/{petId}")
  public PetResponse getPet(@PathVariable UUID petId) {
    return PetResponse.from(petService.getPet(identityApi.currentUser(), petId));
  }

  @PutMapping("/{petId}")
  public PetResponse updatePet(
      @PathVariable UUID petId, @Valid @RequestBody UpsertPetRequest request) {
    return PetResponse.from(
        petService.updatePet(identityApi.currentUser(), petId, request.toCommand()));
  }

  @DeleteMapping("/{petId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePet(@PathVariable UUID petId) {
    petService.deletePet(identityApi.currentUser(), petId);
  }
}
