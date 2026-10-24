-- 按院内日期范围稳定分页；不建立跨模块关联。
CREATE INDEX idx_appointment_reception ON appointment (start_at, id);
