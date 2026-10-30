package com.zjgsu.sfy.owner.dto.req;

import jakarta.validation.constraints.*;

public record ArchiveRequest(
    @NotBlank @Size(max = 255) String reason, @NotNull @PositiveOrZero Long version) {}
