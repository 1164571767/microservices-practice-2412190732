package com.zjgsu.sfy.owner.dto.req;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record BindAccountRequest(
    @NotBlank @Size(max = 50) String username,
    @NotNull UUID expectedUserId,
    @AssertTrue boolean confirmed,
    @NotNull @PositiveOrZero Long version) {}
