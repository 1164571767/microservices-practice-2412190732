package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.owner.PatientContactSummary;

/** 列表批量补充必要联系资料，历史资料缺失时 patient 可空。 */
public record WorkbenchBillResponse(BillSummaryResponse bill, PatientContactSummary patient) {}
