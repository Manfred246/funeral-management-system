import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { dateTime } from './display';
import { Empty, ErrorBlock, Loading, Notice } from './Feedback';

export default function RequestNotes({ request, editable }) {
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(null);
  const [revision, setRevision] = useState(0);
  const path = `/api/requests/${request.uuid}/notes`;
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    api(path, { signal: controller.signal })
      .then(setNotes)
      .catch((error) => {
        if (error.name !== 'AbortError') setLoadError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [path, revision]);
  async function save(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api(editing ? `${path}/${editing.uuid}` : path, {
        method: editing ? 'PUT' : 'POST',
        json: { text: data.get('text').trim(), pinned: data.has('pinned') },
      });
      form.reset();
      setEditing(null);
      setRevision((n) => n + 1);
      setNotice('Заметка сохранена.');
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  async function remove(note) {
    if (!window.confirm('Удалить заметку?')) return;
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api(`${path}/${note.uuid}`, { method: 'DELETE' });
      if (editing?.uuid === note.uuid) setEditing(null);
      setRevision((n) => n + 1);
      setNotice('Заметка удалена.');
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h2>Заметки</h2>
      <ErrorBlock error={loadError} onRetry={() => setRevision((n) => n + 1)} />
      <ErrorBlock error={error} />
      <Notice>{notice}</Notice>
      {loading ? (
        <Loading />
      ) : (
        !loadError &&
        (notes.length ? (
          <div className="notes">
            {notes.map((note) => (
              <article className={`note ${note.pinned ? 'pinned' : ''}`} key={note.uuid}>
                <div className="section-heading">
                  <strong>{note.authorName}</strong>
                  {note.pinned && <span className="badge">Закреплена</span>}
                </div>
                <p className="pre-wrap">{note.text}</p>
                <small className="muted">
                  Создана: {dateTime(note.createdAt)} · Изменена: {dateTime(note.updatedAt)}
                </small>
                {editable && (
                  <div className="actions">
                    <button
                      className="text-button"
                      disabled={busy || request.archived}
                      onClick={() => setEditing(note)}
                    >
                      Изменить
                    </button>
                    <button
                      className="text-button danger-text"
                      disabled={busy || request.archived}
                      onClick={() => remove(note)}
                    >
                      Удалить
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        ) : (
          <Empty>Заметок пока нет.</Empty>
        ))
      )}
      {editable && !request.archived && (
        <form key={editing?.uuid || 'new'} onSubmit={save}>
          <fieldset disabled={busy}>
            <label>
              {editing ? 'Редактирование заметки' : 'Новая заметка'}
              <textarea name="text" rows="3" required defaultValue={editing?.text || ''} />
            </label>
            <label className="checkbox-label">
              <input name="pinned" type="checkbox" defaultChecked={editing?.pinned || false} /> Закрепить
            </label>
            <div className="actions">
              <button type="submit">
                {busy ? 'Сохраняем…' : editing ? 'Сохранить заметку' : 'Добавить заметку'}
              </button>
              {editing && (
                <button type="button" className="secondary" onClick={() => setEditing(null)}>
                  Отмена
                </button>
              )}
            </div>
          </fieldset>
        </form>
      )}
      {request.archived && <p className="muted">Заметки архивной заявки доступны только для чтения.</p>}
    </section>
  );
}
