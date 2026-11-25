ALTER TABLE ceremony_types
    ADD COLUMN code VARCHAR(50),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;

UPDATE ceremony_types
SET code = CASE id
    WHEN 1 THEN 'BURIAL'
    WHEN 2 THEN 'CREMATION'
    ELSE 'LEGACY_' || id::text
END;

ALTER TABLE ceremony_types
    ALTER COLUMN code SET NOT NULL,
    ADD CONSTRAINT uq_ceremony_types_code UNIQUE (code);
