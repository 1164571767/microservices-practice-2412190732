package com.zjgsu.sfy.notification.service;

import java.util.UUID;

/** 宠物通知的投递目标：接收人的 identity 用户 id 与展示用宠物名。 */
public record PetNotificationTarget(UUID recipientUserId, String petName) {}
