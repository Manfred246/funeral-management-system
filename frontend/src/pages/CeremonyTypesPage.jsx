import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { isAdmin, useAuth } from '../auth/AuthContext';
import { Empty, ErrorBlock, Loading, Notice } from '../components/Feedback';

export default function CeremonyTypesPage() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const [types, setTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    api('/api/ceremony-types', { signal: controller.signal })
      .then(setTypes)
      .catch((error) => {
        if (error.name !== 'AbortError') setLoadError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [revision]);
  async function change(path, method, json) {
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api(`/api/ceremony-types${path}`, { method, json });
      setEditing(null);
      setRevision((n) => n + 1);
      setNotice('Справочник обновлён.');
      return true;
    } catch (error) {
      setError(error);
      return false;
    } finally {
      setBusy(false);
    }
  }
  async function create(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    if (await change('', 'POST', { code: fields.get('code').trim(), name: fields.get('name').trim() }))
      form.reset();
  }
  function rename(event) {
    event.preventDefault();
    change(`/${editing.id}`, 'PUT', { name: new FormData(event.currentTarget).get('name').trim() });
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Справочники</p>
          <h1>Типы церемоний</h1>
          <p className="muted">Отключённые типы сохраняются в ранее созданных заявках.</p>
        </div>
      </div>
      <Notice>{notice}</Notice>
      <ErrorBlock error={error} />
      <ErrorBlock error={loadError} onRetry={() => setRevision((n) => n + 1)} />
      <section className="card table-card">
        {loading ? (
          <Loading />
        ) : (
          !loadError &&
          (types.length ? (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Название</th>
                    <th>Код</th>
                    <th>Состояние</th>
                    {admin && <th>Действия</th>}
                  </tr>
                </thead>
                <tbody>
                  {types.map((type) => (
                    <tr key={type.id}>
                      <td>
                        <strong>{type.name}</strong>
                      </td>
                      <td>
                        <code>{type.code}</code>
                      </td>
                      <td>
                        <span className={`badge ${type.enabled ? 'enabled' : ''}`}>
                          {type.enabled ? 'Включён' : 'Отключён'}
                        </span>
                      </td>
                      {admin && (
                        <td>
                          <div className="actions">
                            <button className="secondary" disabled={busy} onClick={() => setEditing(type)}>
                              Переименовать
                            </button>
                            <button
                              className="secondary"
                              disabled={busy}
                              onClick={() =>
                                change(`/${type.id}/${type.enabled ? 'disable' : 'enable'}`, 'POST')
                              }
                            >
                              {type.enabled ? 'Отключить' : 'Включить'}
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty />
          ))
        )}
      </section>
      {admin && (
        <>
          {editing && (
            <section className="card">
              <h2>Переименовать «{editing.name}»</h2>
              <form key={editing.id} onSubmit={rename}>
                <fieldset className="actions" disabled={busy}>
                  <label>
                    Название
                    <input name="name" required maxLength={100} defaultValue={editing.name} />
                  </label>
                  <button type="submit">Сохранить</button>
                  <button type="button" className="secondary" onClick={() => setEditing(null)}>
                    Отмена
                  </button>
                </fieldset>
              </form>
            </section>
          )}
          <details className="card">
            <summary>Добавить тип церемонии</summary>
            <form onSubmit={create}>
              <fieldset disabled={busy}>
                <div className="form-grid">
                  <label>
                    Код
                    <input name="code" required maxLength={50} placeholder="Например, MEMORIAL" />
                  </label>
                  <label>
                    Название
                    <input name="name" required maxLength={100} />
                  </label>
                </div>
                <button type="submit">Создать тип</button>
              </fieldset>
            </form>
          </details>
        </>
      )}
    </>
  );
}
