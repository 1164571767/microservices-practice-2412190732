package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.service.command.UpsertOwnerCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "主人档案的完整内容（创建和更新都用它）")
public record UpsertOwnerRequest(
    @Schema(description = "主人姓名，最多 100 字", example = "张三") @NotBlank @Size(max = 100) String name,
    @Schema(description = "联系电话，可带国家码 +，6–20 位数字", example = "13800000000")
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "手机号格式不正确")
        String phone,
    @Schema(description = "联系地址，可空，最多 255 字", example = "杭州市西湖区文三路 100 号") @Size(max = 255)
        String address) {

  public UpsertOwnerCommand toCommand() {
    return new UpsertOwnerCommand(name, phone, address);
  }
}
