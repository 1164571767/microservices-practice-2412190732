-- M7 站内通知。notification 拥有通知与提醒记录。
-- recipient_user_id 为 identity 模块引用，source_id 为事件来源（预约 / 账单 / 病历 / 药品）引用，
-- 均为跨模块引用，故意不建跨模块外键。

CREATE TABLE notification (
    id BINARY(16) NOT NULL,
    recipient_user_id BINARY(16) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_id BINARY(16) NOT NULL,
    -- 消费幂等的最终守卫：同一事件对同一接收人只允许一条通知。
    dedupe_key VARCHAR(200) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_notification_type CHECK (
        type IN ('APPOINTMENT_BOOKED', 'QUEUE_CALLED', 'BILL_PAID', 'REVISIT_REMINDER', 'LOW_STOCK')
    ),
    CONSTRAINT ck_notification_status CHECK (status IN ('UNREAD', 'READ')),
    CONSTRAINT ck_notification_title_not_blank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_notification_body_not_blank CHECK (CHAR_LENGTH(TRIM(body)) > 0),
    CONSTRAINT ck_notification_dedupe_not_blank CHECK (CHAR_LENGTH(TRIM(dedupe_key)) > 0),
    -- 已读时间与状态必须一致，避免「已读但没有阅读时间」。
    CONSTRAINT ck_notification_read_at CHECK (
        (status = 'UNREAD' AND read_at IS NULL)
        OR (status = 'READ' AND read_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notification_recipient_created ON notification (recipient_user_id, created_at DESC);
CREATE INDEX idx_notification_recipient_status ON notification (recipient_user_id, status);
CREATE INDEX idx_notification_source ON notification (type, source_id);
