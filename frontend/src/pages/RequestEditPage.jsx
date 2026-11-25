import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { api } from '../api/client';
import { ErrorBlock, Loading } from '../components/Feedback';
import RequestForm from '../components/RequestForm';
import useLookups from '../components/useLookups';

export default function RequestEditPage() {
  const { uuid } = useParams();
  const navigate = useNavigate();
  const lookups = useLookups();
  const [request, setRequest] = useState(null);
  const [loading, setLoading] = useState(Boolean(uuid));
  const [loadError, setLoadError] = useState(null);
  const [saveError, setSaveError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!uuid) {
      setRequest(null);
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    setSaveError(null);
    api(`/api/requests/${uuid}`, { signal: controller.signal })
      .then(setRequest)
      .catch((error) => {
        if (error.name !== 'AbortError') setLoadError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [uuid, revision]);
  async function save(data) {
    setBusy(true);
    setSaveError(null);
    try {
      const saved = await api(uuid ? `/api/requests/${uuid}` : '/api/requests', {
        method: uuid ? 'PUT' : 'POST',
        json: data,
      });
      navigate(`/requests/${saved.uuid}`, {
        replace: true,
        state: { message: uuid ? 'Изменения сохранены.' : 'Заявка создана.' },
      });
    } catch (error) {
      setSaveError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="back-link" to={uuid ? `/requests/${uuid}` : '/requests'}>
        ← {uuid ? 'К заявке' : 'К списку заявок'}
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Заявки</p>
          <h1>{uuid ? 'Редактирование заявки' : 'Новая заявка'}</h1>
          <p className="muted">{request?.requestCode || 'Заполните данные обращения'}</p>
        </div>
      </div>
      <section className="card">
        <ErrorBlock error={lookups.error} onRetry={lookups.reload} />
        <ErrorBlock error={loadError} onRetry={() => setRevision((n) => n + 1)} />
        <ErrorBlock error={saveError} />
        {uuid &&
          saveError?.status === 409 &&
          saveError.message.startsWith('Запись была изменена другим пользователем') && (
            <div className="message warning">
              <p>Запись была изменена другим пользователем. Обновите данные.</p>
              <button className="secondary" disabled={busy} onClick={() => setRevision((n) => n + 1)}>
                Загрузить актуальную запись (заменит форму)
              </button>
            </div>
          )}
        {loading || lookups.loading ? (
          <Loading />
        ) : (
          !loadError &&
          !lookups.error &&
          (!uuid || request) && (
            <RequestForm
              key={`${uuid || 'new'}-${revision}`}
              initial={uuid ? request : null}
              clients={lookups.clients}
              types={lookups.types}
              busy={busy}
              onSubmit={save}
            />
          )
        )}
      </section>
    </>
  );
}
