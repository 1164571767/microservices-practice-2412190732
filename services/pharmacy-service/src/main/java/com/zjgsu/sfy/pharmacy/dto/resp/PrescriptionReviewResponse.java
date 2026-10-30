package com.zjgsu.sfy.pharmacy.dto.resp;

import com.zjgsu.sfy.medical.PharmacyPrescription;
import com.zjgsu.sfy.owner.PatientContactSummary;
import com.zjgsu.sfy.owner.PharmacyAllergy;
import java.util.List;

public record PrescriptionReviewResponse(
    PharmacyPrescription prescription,
    PatientContactSummary patient,
    String doctorName,
    List<PharmacyAllergy> allergies,
    DispenseResponse dispense) {}
