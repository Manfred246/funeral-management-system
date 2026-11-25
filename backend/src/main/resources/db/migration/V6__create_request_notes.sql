CREATE TABLE request_notes (
    uuid UUID PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES funeral_requests(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    text TEXT NOT NULL CHECK (btrim(text) <> ''),
    pinned BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_request_notes_request_order
    ON request_notes(request_id, pinned DESC, created_at DESC, uuid ASC);
CREATE INDEX idx_request_notes_author_id ON request_notes(author_id);
