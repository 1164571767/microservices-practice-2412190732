package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Owner;
import java.time.Instant;
import java.util.UUID;

public record OwnerResponse(
    UUID id,
    UUID userId,
    String name,
    String phone,
    String address,
    Instant createdAt,
    Instant updatedAt) {

  public static OwnerResponse from(Owner owner) {
    return new OwnerResponse(
        owner.getId(),
        owner.getUserId(),
        owner.getName(),
        owner.getPhone(),
        owner.getAddress(),
        owner.getCreatedAt(),
        owner.getUpdatedAt());
  }
}
