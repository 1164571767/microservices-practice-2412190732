package com.zjgsu.sfy.owner.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerTest {

  @Test
  void unboundClientNeverBelongsToAnArbitraryUserAndBindingCannotBeOverwritten() {
    Owner owner = Owner.createUnbound("客户", "13800138000", null);
    assertThat(owner.getUserId()).isNull();
    assertThat(owner.belongsTo(null)).isFalse();
    UUID account = UUID.randomUUID();
    assertThat(owner.belongsTo(account)).isFalse();
    owner.bind(account);
    assertThat(owner.belongsTo(account)).isTrue();
    assertThatThrownBy(() -> owner.bind(UUID.randomUUID()))
        .isInstanceOf(com.zjgsu.sfy.common.error.BusinessException.class);
    assertThat(owner.getUserId()).isEqualTo(account);
  }

  @Test
  void archivePreservesContactAndBlocksChangesOrBinding() {
    Owner owner = Owner.createUnbound("客户", "13800138000", "杭州");
    UUID actor = UUID.randomUUID();
    owner.archive(actor, " 重复档案人工核实 ");
    assertThat(owner.isArchived()).isTrue();
    assertThat(owner.getArchivedBy()).isEqualTo(actor);
    assertThat(owner.getArchiveReason()).isEqualTo("重复档案人工核实");
    assertThat(owner.getPhone()).isEqualTo("13800138000");
    assertThatThrownBy(() -> owner.updateDetails("覆盖", "13900139000", null))
        .isInstanceOf(com.zjgsu.sfy.common.error.BusinessException.class);
    assertThatThrownBy(() -> owner.bind(UUID.randomUUID()))
        .isInstanceOf(com.zjgsu.sfy.common.error.BusinessException.class);
  }

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
