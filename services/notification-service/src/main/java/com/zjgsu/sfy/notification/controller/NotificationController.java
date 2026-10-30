package com.zjgsu.sfy.notification.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.dto.resp.MarkAllReadResponse;
import com.zjgsu.sfy.notification.dto.resp.NotificationResponse;
import com.zjgsu.sfy.notification.dto.resp.PageResponse;
import com.zjgsu.sfy.notification.dto.resp.UnreadCountResponse;
import com.zjgsu.sfy.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 站内通知查询与已读接口；资源范围固定为当前登录用户，不接受客户端指定接收人。 */
@RestController
@RequestMapping("/api/notification/notifications")
@Tag(name = "站内通知", description = "本人通知的查询与已读操作；通知由系统事件生成，没有写接口")
public class NotificationController {

  private final NotificationService notificationService;
  private final IdentityApi identityApi;

  public NotificationController(NotificationService notificationService, IdentityApi identityApi) {
    this.notificationService = notificationService;
    this.identityApi = identityApi;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "我的通知列表", description = "需要登录。分页返回本人收到的通知，创建时间倒序，可按已读/未读筛选；只能看自己的通知。")
  public PageResponse<NotificationResponse> list(
      @Parameter(description = "阅读状态筛选：UNREAD 未读 / READ 已读，可空") @RequestParam(required = false)
          NotificationStatus status,
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    return PageResponse.of(
        notificationService.list(identityApi.currentUser(), status, pageable),
        NotificationResponse::from);
  }

  @GetMapping("/unread-count")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "我的未读通知数", description = "需要登录。返回本人当前未读通知条数，适合放在导航栏做未读角标。")
  public UnreadCountResponse unreadCount() {
    return new UnreadCountResponse(notificationService.unreadCount(identityApi.currentUser()));
  }

  @GetMapping("/{notificationId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "通知详情",
      description = "需要登录。只能查看自己的通知；查看别人的通知返回 AUTH_FORBIDDEN。查看详情不会自动标记已读。")
  public NotificationResponse get(
      @Parameter(description = "通知 ID", example = "4f5a6b7c-8d9e-4f0a-9b1c-2d3e4f5a6b7c")
          @PathVariable
          UUID notificationId) {
    return NotificationResponse.from(
        notificationService.get(identityApi.currentUser(), notificationId));
  }

  @PostMapping("/{notificationId}/read")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "标记单条已读", description = "需要登录。把本人一条未读通知标为已读；重复调用不会改动首次的已读时间。")
  public NotificationResponse markRead(
      @Parameter(description = "通知 ID", example = "4f5a6b7c-8d9e-4f0a-9b1c-2d3e4f5a6b7c")
          @PathVariable
          UUID notificationId) {
    return NotificationResponse.from(
        notificationService.markRead(identityApi.currentUser(), notificationId));
  }

  @PostMapping("/read-all")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "一键全部已读", description = "需要登录。把本人所有未读通知标为已读，返回本次实际更新的条数；没有未读时返回 0。")
  public MarkAllReadResponse markAllRead() {
    return new MarkAllReadResponse(notificationService.markAllRead(identityApi.currentUser()));
  }
}
