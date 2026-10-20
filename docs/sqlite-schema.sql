CREATE TABLE IF NOT EXISTS bot_conversation_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    message_role TEXT NOT NULL,
    content TEXT NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_conversation_message_conversation_id_id
    ON bot_conversation_message (conversation_id, id);

CREATE INDEX IF NOT EXISTS idx_conversation_message_conversation_id_create_time
    ON bot_conversation_message (conversation_id, create_time);

CREATE TABLE IF NOT EXISTS bot_conversation_summary (
    conversation_id TEXT PRIMARY KEY,
    summary        TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ilink_inbound_message (
    bot_id            TEXT    NOT NULL,
    message_id        INTEGER NOT NULL,
    sender_id         TEXT    NOT NULL,
    conversation_id   TEXT    NOT NULL,
    status            TEXT    NOT NULL,
    attempt_count     INTEGER NOT NULL DEFAULT 0,
    first_received_at TEXT    NOT NULL,
    last_received_at  TEXT    NOT NULL,
    lease_id          TEXT,
    lease_until       TEXT,
    next_attempt_at   TEXT,
    last_error        TEXT,
    completed_at      TEXT,
    PRIMARY KEY (bot_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_ilink_inbound_message_status_retry
    ON ilink_inbound_message (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_ilink_inbound_message_conversation_received
    ON ilink_inbound_message (conversation_id, last_received_at);

CREATE TABLE IF NOT EXISTS desktop_task (
    task_id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    url TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    approval_expires_at TEXT NOT NULL,
    approved_at TEXT,
    finished_at TEXT,
    result_code TEXT,
    result_summary TEXT
);

CREATE INDEX IF NOT EXISTS idx_desktop_task_conversation_id_created_at
    ON desktop_task (conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS student_todo (
    id              TEXT PRIMARY KEY,
    conversation_id TEXT    NOT NULL,
    title           TEXT    NOT NULL,
    due_time        TEXT    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'PENDING',
    source          TEXT,
    created_at      TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_todo_conversation_id_due_time
    ON student_todo (conversation_id, due_time ASC);
