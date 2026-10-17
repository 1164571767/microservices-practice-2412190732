package com.zjgsu.sfy.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void createNormalizesUsernamePhoneAndDisplayName() {
    User user =
        User.create(
            "  Alice.W  ", " 13800000000 ", "{bcrypt}hash", "  Alice  ", Set.of(Role.OWNER));

    assertThat(user.getUsername()).isEqualTo("alice.w");
    assertThat(user.getPhone()).isEqualTo("13800000000");
    assertThat(user.getDisplayName()).isEqualTo("Alice");
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void newUserIsActive() {
    User user = User.create("bob", "13900000000", "{bcrypt}hash", "Bob", Set.of(Role.OWNER));

    assertThat(user.isActive()).isTrue();
  }

  @Test
  void createWithoutRolesIsRejected() {
    assertThatThrownBy(() -> User.create("carl", "13700000000", "{bcrypt}hash", "Carl", Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rolesAreNotExternallyMutable() {
    User user = User.create("dana", "13600000000", "{bcrypt}hash", "Dana", Set.of(Role.OWNER));

    assertThatThrownBy(() -> user.getRoles().add(Role.ADMIN))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void normalizeUsernameIsCaseInsensitiveAndTrimmed() {
    assertThat(User.normalizeUsername("  FOO.Bar ")).isEqualTo("foo.bar");
  }
}
