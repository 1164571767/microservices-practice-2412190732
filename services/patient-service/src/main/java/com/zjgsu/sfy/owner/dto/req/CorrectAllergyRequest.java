package com.zjgsu.sfy.owner.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CorrectAllergyRequest(
    @NotNull @Valid CreateAllergyRequest record, @NotBlank @Size(max = 255) String reason) {}
