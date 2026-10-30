package com.zjgsu.sfy.appointment.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleChangeRequest(
    @NotNull @Min(0) Long version, @NotBlank @Size(max = 500) String reason) {}
