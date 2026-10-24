package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.medical.BillingRecordSummary;
import java.util.UUID;

public record BillingRecordResponse(
    BillingRecordSummary record,
    String doctorName,
    UUID existingBillId,
    String existingBillStatus) {}
