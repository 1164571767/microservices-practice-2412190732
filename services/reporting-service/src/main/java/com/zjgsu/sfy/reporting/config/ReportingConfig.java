package com.zjgsu.sfy.reporting.config;

import com.zjgsu.sfy.reporting.common.ReportingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册统计报表配置；仅 reporting 需要，收敛在本模块内。 */
@Configuration
@EnableConfigurationProperties(ReportingProperties.class)
public class ReportingConfig {}
