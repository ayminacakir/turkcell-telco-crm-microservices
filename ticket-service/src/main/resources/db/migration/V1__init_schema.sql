CREATE SCHEMA IF NOT EXISTS ticket_service;

CREATE TABLE IF NOT EXISTS ticket_service.tickets (
    id           UUID PRIMARY KEY,
    customer_id  UUID NOT NULL,
    category     VARCHAR(50) NOT NULL,
    priority     VARCHAR(20) NOT NULL,
    status       VARCHAR(30) NOT NULL,
    sla_due_at   TIMESTAMP,
    created_at   TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_service.ticket_comments (
    id          UUID PRIMARY KEY,
    ticket_id   UUID NOT NULL REFERENCES ticket_service.tickets(id),
    author_id   UUID NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_id ON ticket_service.ticket_comments(ticket_id);
