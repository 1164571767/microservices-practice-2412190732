package com.zjgsu.sfy.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.entity.Notification;
import com.zjgsu.sfy.notification.support.NotificationIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 通知查询与已读接口：归属校验、分页排序、状态过滤与幂等。 */
class NotificationQueryIntegrationTest extends NotificationIntegrationTest {

  private static final String BASE = "/api/notification/notifications";

  @Test
  void listsOwnNotificationsNewestFirst() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    Notification first =
        seedNotification(user, NotificationType.BILL_PAID, UUID.randomUUID(), "第一条");
    Notification second =
        seedNotification(user, NotificationType.APPOINTMENT_BOOKED, UUID.randomUUID(), "第二条");

    mockMvc
        .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items[0].id").value(second.getId().toString()))
        .andExpect(jsonPath("$.items[1].id").value(first.getId().toString()))
        .andExpect(jsonPath("$.items[0].type").value("APPOINTMENT_BOOKED"));
  }

  @Test
  void filtersByStatusAndCountsUnread() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    seedNotification(user, NotificationType.BILL_PAID, UUID.randomUUID(), "未读一");
    seedNotification(user, NotificationType.QUEUE_CALLED, UUID.randomUUID(), "未读二");
    Notification read = seedNotification(user, NotificationType.LOW_STOCK, UUID.randomUUID(), "已读");

    mockMvc
        .perform(
            post(BASE + "/" + read.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READ"));

    mockMvc
        .perform(
            get(BASE).param("status", "UNREAD").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items.length()").value(2));

    mockMvc
        .perform(get(BASE + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unread").value(2));
  }

  @Test
  void markingReadIsIdempotent() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    Notification notification =
        seedNotification(user, NotificationType.REVISIT_REMINDER, UUID.randomUUID(), "复诊");

    String first =
        mockMvc
            .perform(
                post(BASE + "/" + notification.getId() + "/read")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READ"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mockMvc
            .perform(
                post(BASE + "/" + notification.getId() + "/read")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READ"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<String>read(second, "$.readAt"))
        .isEqualTo(JsonPath.<String>read(first, "$.readAt"));
    assertThat(unreadCountOf(user.getId())).isZero();
  }

  @Test
  void markAllReadOnlyAffectsOwnUnread() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    User other = seedUser(Set.of(Role.OWNER));
    seedNotification(user, NotificationType.BILL_PAID, UUID.randomUUID(), "一");
    seedNotification(user, NotificationType.QUEUE_CALLED, UUID.randomUUID(), "二");
    seedNotification(other, NotificationType.BILL_PAID, UUID.randomUUID(), "他人");

    mockMvc
        .perform(post(BASE + "/read-all").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated").value(2));
    mockMvc
        .perform(post(BASE + "/read-all").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated").value(0));

    assertThat(unreadCountOf(user.getId())).isZero();
    assertThat(unreadCountOf(other.getId())).isEqualTo(1);
  }

  @Test
  void cannotReadAnotherUsersNotification() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User other = seedUser(Set.of(Role.OWNER));
    Notification notification =
        seedNotification(owner, NotificationType.BILL_PAID, UUID.randomUUID(), "他人账单");

    mockMvc
        .perform(
            get(BASE + "/" + notification.getId()).header(HttpHeaders.AUTHORIZATION, bearer(other)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    mockMvc
        .perform(
            post(BASE + "/" + notification.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    assertThat(unreadCountOf(owner.getId())).isEqualTo(1);
  }

  @Test
  void unknownNotificationReturnsNotFound() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            get(BASE + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc
        .perform(get(BASE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void invalidStatusOrIdIsRejectedAsValidationFailure() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    String token = bearer(user);

    mockMvc
        .perform(get(BASE).param("status", "BOGUS").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mockMvc
        .perform(get(BASE + "/not-a-uuid").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
