package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.service.command.UpsertOwnerCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertOwnerRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "手机号格式不正确") String phone,
    @Size(max = 255) String address) {

  public UpsertOwnerCommand toCommand() {
    return new UpsertOwnerCommand(name, phone, address);
  }
}
