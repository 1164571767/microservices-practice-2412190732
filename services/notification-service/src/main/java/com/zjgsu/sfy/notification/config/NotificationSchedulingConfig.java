package com.zjgsu.sfy.notification.config;

import com.zjgsu.sfy.notification.common.RevisitReminderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 仅 notification 依赖定时任务，故把调度开关收在本模块配置中，避免全局默认开启。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RevisitReminderProperties.class)
public class NotificationSchedulingConfig {}
