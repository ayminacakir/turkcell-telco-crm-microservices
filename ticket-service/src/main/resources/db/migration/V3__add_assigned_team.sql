ALTER TABLE ticket_service.tickets
    ADD COLUMN IF NOT EXISTS assigned_team VARCHAR(100);
