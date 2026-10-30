package com.zjgsu.sfy.appointment.service.result;

import java.time.Instant;

/** 可预约时段。 */
public record TimeSlot(Instant startAt, Instant endAt) {}
