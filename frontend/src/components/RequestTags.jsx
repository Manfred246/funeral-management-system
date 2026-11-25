import { useState } from 'react';
import { api } from '../api/client';
import { Empty, ErrorBlock, Notice } from './Feedback';

export default function RequestTags({ request, tags, editable, onChange, onTagsChange }) {
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  async function changeTag(tagId, remove = false) {
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api(`/api/requests/${request.uuid}/tags/${tagId}`, { method: remove ? 'DELETE' : 'POST' });
      setNotice(remove ? 'Тег удалён из заявки.' : 'Тег добавлен.');
      onChange();
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  async function createTag(event) {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api('/api/tags', { method: 'POST', json: { name: new FormData(form).get('name').trim() } });
      form.reset();
      onTagsChange();
      setNotice('Тег создан. Теперь его можно добавить к заявке.');
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h2>Теги</h2>
      <ErrorBlock error={error} />
      <Notice>{notice}</Notice>
      <div className="tag-list">
        {request.tags?.length ? (
          request.tags.map((tag) => (
            <span className="tag" key={tag.id}>
              {tag.name}
              {editable && (
                <button
                  type="button"
                  aria-label={`Удалить тег ${tag.name}`}
                  disabled={busy || request.archived}
                  onClick={() => changeTag(tag.id, true)}
                >
                  ×
                </button>
              )}
            </span>
          ))
        ) : (
          <Empty>У заявки нет тегов.</Empty>
        )}
      </div>
      {editable && (
        <>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              changeTag(Number(new FormData(e.currentTarget).get('tagId')));
            }}
          >
            <fieldset className="actions" disabled={busy || request.archived}>
              <label>
                Добавить существующий тег
                <select name="tagId" required>
                  <option value="">Выберите тег</option>
                  {tags
                    .filter((tag) => !request.tags?.some((current) => current.id === tag.id))
                    .map((tag) => (
                      <option key={tag.id} value={tag.id}>
                        {tag.name}
                      </option>
                    ))}
                </select>
              </label>
              <button type="submit" className="secondary">
                Добавить
              </button>
            </fieldset>
          </form>
          <details>
            <summary>Создать новый тег</summary>
            <form onSubmit={createTag}>
              <fieldset className="actions" disabled={busy}>
                <label>
                  Название
                  <input name="name" required maxLength={50} />
                </label>
                <button type="submit" className="secondary">
                  Создать тег
                </button>
              </fieldset>
            </form>
          </details>
        </>
      )}
    </section>
  );
}
