package com.zjgsu.sfy.owner.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CorrectVaccinationRequest(
    @NotNull @Valid CreateVaccinationRequest record, @NotBlank @Size(max = 255) String reason) {}
