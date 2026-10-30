package com.zjgsu.sfy.owner.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ClinicPetWriteRequest(
    @NotNull @Valid UpsertPetRequest details,
    @NotNull Boolean birthDateEstimated,
    @NotNull @PositiveOrZero Long version) {}
