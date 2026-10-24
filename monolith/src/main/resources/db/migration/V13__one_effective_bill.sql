-- 仅 VOID 释放开单资格；历史 PAID / REFUNDED 均保留资格。
-- 若旧库有重复有效单，本迁移主动失败；需人工核对，不自动作废或删除历史财务数据。
ALTER TABLE bill
  ADD COLUMN effective_record_id BINARY(16)
    GENERATED ALWAYS AS (CASE WHEN status <> 'VOID' THEN medical_record_id ELSE NULL END) STORED,
  ADD CONSTRAINT uq_bill_effective_record UNIQUE (effective_record_id);
CREATE INDEX idx_bill_created_id ON bill (created_at DESC, id DESC);
CREATE INDEX idx_bill_status_created_id ON bill (status, created_at DESC, id DESC);
