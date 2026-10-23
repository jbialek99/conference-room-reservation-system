ALTER TABLE reservations
    ADD COLUMN integration_state ENUM(
        'CREATING',
        'DEMO',
        'FAILED',
        'LINKED',
        'UNCERTAIN'
    ) NULL,
    ADD COLUMN graph_transaction_id VARCHAR(255) NULL,
    ADD UNIQUE INDEX uk_reservations_graph_transaction_id (
        graph_transaction_id
    );

UPDATE reservations
SET integration_state =
    CASE
        WHEN outlook_event_id IS NOT NULL
             AND TRIM(outlook_event_id) <> ''
        THEN 'LINKED'
        ELSE 'UNCERTAIN'
    END
WHERE integration_state IS NULL;