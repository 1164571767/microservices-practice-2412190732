package com.zjgsu.sfy.appointment;

import java.time.Instant;
import java.util.UUID;

/** 叫号事件：候诊队列被叫到下一号时发布，供 notification 模块订阅。 */
public record QueueNumberCalled(UUID appointmentId, UUID petId, UUID doctorId, Instant calledAt) {}
