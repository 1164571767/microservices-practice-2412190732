package com.zjgsu.sfy.owner.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerTest {

  @Test
  void createTrimsFieldsAndBlanksAddressToNull() {
    Owner owner = Owner.create(UUID.randomUUID(), " 张三 ", " 13800000000 ", "   ");

    assertThat(owner.getName()).isEqualTo("张三");
    assertThat(owner.getPhone()).isEqualTo("13800000000");
    assertThat(owner.getAddress()).isNull();
  }

  @Test
  void updateDetailsReplacesFields() {
    Owner owner = Owner.create(UUID.randomUUID(), "张三", "13800000000", null);

    owner.updateDetails(" 李四 ", " 13900000000 ", " 杭州市 ");

    assertThat(owner.getName()).isEqualTo("李四");
    assertThat(owner.getPhone()).isEqualTo("13900000000");
    assertThat(owner.getAddress()).isEqualTo("杭州市");
  }

  @Test
  void createWithBlankNameIsRejected() {
    assertThatThrownBy(() -> Owner.create(UUID.randomUUID(), "  ", "13800000000", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createWithBlankPhoneIsRejected() {
    assertThatThrownBy(() -> Owner.create(UUID.randomUUID(), "张三", " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void belongsToComparesUserId() {
    UUID userId = UUID.randomUUID();
    Owner owner = Owner.create(userId, "张三", "13800000000", null);

    assertThat(owner.belongsTo(userId)).isTrue();
    assertThat(owner.belongsTo(UUID.randomUUID())).isFalse();
  }
}
