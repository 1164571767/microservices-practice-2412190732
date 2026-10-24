package com.zjgsu.sfy.owner.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ClientWriteRequest(
    @NotNull @Valid UpsertOwnerRequest details, @NotNull @PositiveOrZero Long version) {}
