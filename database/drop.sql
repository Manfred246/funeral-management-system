-- Destructive reset: run only in a disposable/test database or after a backup.
-- Dependency order is explicit; unrelated objects are not removed with CASCADE.
BEGIN;

DROP TABLE IF EXISTS funeral_requests;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS request_statuses;
DROP TABLE IF EXISTS ceremony_types;

COMMIT;
