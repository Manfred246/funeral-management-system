-- DROP EXPRESSION сохраняет значения, тип и прежние UNIQUE/NOT NULL.
ALTER TABLE funeral_requests ALTER COLUMN request_code DROP EXPRESSION;
ALTER TABLE funeral_requests ADD CONSTRAINT chk_funeral_requests_request_code
    CHECK (btrim(request_code) <> '');

CREATE FUNCTION set_funeral_request_code() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.request_code IS DISTINCT FROM OLD.request_code THEN
            RAISE EXCEPTION 'request_code cannot be changed after creation' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.request_code IS NULL THEN
        NEW.request_code := 'FR-' || EXTRACT(YEAR FROM NEW.created_at)::integer::text || '-'
            || lpad(NEW.id::text, GREATEST(6, length(NEW.id::text)), '0');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_funeral_request_code
    BEFORE INSERT OR UPDATE ON funeral_requests
    FOR EACH ROW EXECUTE FUNCTION set_funeral_request_code();
