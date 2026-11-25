ALTER TABLE funeral_requests
    ADD COLUMN uuid UUID,
    ADD COLUMN request_code VARCHAR(64) GENERATED ALWAYS AS (
        'FR-' || EXTRACT(YEAR FROM created_at)::integer::text || '-'
        || lpad(id::text, GREATEST(6, length(id::text)), '0')
    ) STORED,
    ADD COLUMN urgent BOOLEAN DEFAULT false,
    ADD COLUMN priority INTEGER DEFAULT 0,
    ADD COLUMN archived BOOLEAN DEFAULT false,
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN version BIGINT DEFAULT 0;

UPDATE funeral_requests
SET uuid = gen_random_uuid(),
    urgent = false,
    priority = CASE
        WHEN ceremony_date - created_at::date BETWEEN 0 AND 3 THEN 2
        WHEN ceremony_date - created_at::date BETWEEN 4 AND 7 THEN 1
        ELSE 0
    END,
    archived = false,
    updated_at = created_at,
    version = 0;

-- Вычисляемый код и DEFAULT позволяют запускать прежний seed.sql после миграции.
ALTER TABLE funeral_requests
    ALTER COLUMN uuid SET DEFAULT gen_random_uuid(),
    ALTER COLUMN uuid SET NOT NULL,
    ALTER COLUMN request_code SET NOT NULL,
    ALTER COLUMN urgent SET NOT NULL,
    ALTER COLUMN priority SET NOT NULL,
    ALTER COLUMN archived SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN version SET NOT NULL,
    ADD CONSTRAINT uq_funeral_requests_uuid UNIQUE (uuid),
    ADD CONSTRAINT uq_funeral_requests_request_code UNIQUE (request_code);
