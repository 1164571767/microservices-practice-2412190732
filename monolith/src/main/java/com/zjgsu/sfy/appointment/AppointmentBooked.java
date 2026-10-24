package com.zjgsu.sfy.appointment;

import java.time.Instant;
import java.util.UUID;

/** 预约成功事件：新建预约（幂等回放不会重复发布）后发布，供 notification 模块生成站内通知。 */
public record AppointmentBooked(
    UUID appointmentId,
    UUID petId,
    UUID doctorId,
    Instant startAt,
    Instant endAt,
    Instant bookedAt) {}
