package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Owner;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "主人档案")
public record OwnerResponse(
    @Schema(description = "主人档案 ID", example = "2d7e9a41-3b6c-4f82-9d10-8a5c7e3b1f04") UUID id,
    @Schema(description = "关联的登录账号 ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID userId,
    @Schema(description = "主人姓名", example = "张三") String name,
    @Schema(description = "联系电话", example = "13800000000") String phone,
    @Schema(description = "联系地址", example = "杭州市西湖区文三路 100 号") String address,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    @Schema(description = "最后更新时间（UTC）", example = "2026-10-20T01:05:00Z") Instant updatedAt,
    long version,
    boolean archived,
    Instant archivedAt,
    UUID archivedBy,
    String archiveReason) {

  public static OwnerResponse from(Owner owner) {
    return new OwnerResponse(
        owner.getId(),
        owner.getUserId(),
        owner.getName(),
        owner.getPhone(),
        owner.getAddress(),
        owner.getCreatedAt(),
        owner.getUpdatedAt(),
        owner.getVersion(),
        owner.isArchived(),
        owner.getArchivedAt(),
        owner.getArchivedBy(),
        owner.getArchiveReason());
  }
}
