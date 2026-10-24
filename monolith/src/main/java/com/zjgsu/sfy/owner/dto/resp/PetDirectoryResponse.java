package com.zjgsu.sfy.owner.dto.resp;

public record PetDirectoryResponse(
    PetResponse pet, String ownerName, String ownerPhone, boolean ownerArchived) {}
