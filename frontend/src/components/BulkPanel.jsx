import { useState } from 'react';
import { api } from '../api/client';
import { statuses } from './display';
import { ErrorBlock } from './Feedback';

const operations = {
  ARCHIVE: 'Архивировать',
  RESTORE: 'Восстановить',
  CHANGE_STATUS: 'Изменить статус',
  ADD_TAG: 'Добавить тег',
  REMOVE_TAG: 'Удалить тег',
};

export default function BulkPanel({ selected, tags, onComplete, onClear }) {
  const [operation, setOperation] = useState('ARCHIVE');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const body = { operation, uuids: selected };
    if (operation === 'CHANGE_STATUS') body.status = data.get('status');
    if (operation === 'ADD_TAG' || operation === 'REMOVE_TAG') body.tagId = Number(data.get('tagId'));
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const response = await api('/api/requests/bulk', { method: 'POST', json: body });
      setResult(response);
      onComplete();
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card bulk-panel">
      <h2>
        Массовые операции <span className="count">Выбрано: {selected.length}</span>
      </h2>
      <form onSubmit={submit}>
        <fieldset className="actions" disabled={busy}>
          <label>
            Действие
            <select
              value={operation}
              onChange={(e) => {
                setOperation(e.target.value);
                setResult(null);
              }}
            >
              {Object.entries(operations).map(([key, name]) => (
                <option key={key} value={key}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          {operation === 'CHANGE_STATUS' && (
            <label>
              Новый статус
              <select name="status" required>
                {Object.entries(statuses).map(([key, name]) => (
                  <option key={key} value={key}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
          )}
          {(operation === 'ADD_TAG' || operation === 'REMOVE_TAG') && (
            <label>
              Тег
              <select name="tagId" required>
                <option value="">Выберите тег</option>
                {tags.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <button type="submit" disabled={!selected.length}>
            {busy ? 'Выполняем…' : 'Выполнить'}
          </button>
          <button type="button" className="secondary" onClick={onClear} disabled={!selected.length}>
            Снять выбор
          </button>
        </fieldset>
      </form>
      <ErrorBlock error={error} />
      {result && (
        <div className="operation-result" role="status">
          <strong>
            Успешно: {result.successful.length} · Ошибок: {result.errors.length}
          </strong>
          {result.successful.length > 0 && (
            <details>
              <summary>Успешные заявки</summary>
              <ul>
                {result.successful.map((uuid) => (
                  <li key={uuid}>
                    <code>{uuid}</code>
                  </li>
                ))}
              </ul>
            </details>
          )}
          {result.errors.length > 0 && (
            <ul className="error-list">
              {result.errors.map((item, index) => (
                <li key={index}>
                  <code>{item.uuid}</code>: {item.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
