package com.zjgsu.sfy.medical.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicalRecordTest {

  private static MedicalRecord createRecord(String symptoms, String diagnosis, String advice) {
    return MedicalRecord.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        symptoms,
        diagnosis,
        advice,
        Instant.parse("2026-11-10T01:00:00Z"));
  }

  @Test
  void createTrimsTextAndNullsBlankAdvice() {
    MedicalRecord record = createRecord("  咳嗽  ", " 支气管炎 ", "   ");

    assertThat(record.getSymptoms()).isEqualTo("咳嗽");
    assertThat(record.getDiagnosis()).isEqualTo("支气管炎");
    assertThat(record.getAdvice()).isNull();
  }

  @Test
  void createRejectsBlankSymptomsOrDiagnosis() {
    assertThatThrownBy(() -> createRecord(" ", "支气管炎", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> createRecord("咳嗽", " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullVisitedAt() {
    assertThatThrownBy(
            () ->
                MedicalRecord.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "咳嗽",
                    "支气管炎",
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }
}
