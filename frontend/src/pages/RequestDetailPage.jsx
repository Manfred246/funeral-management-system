import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { api } from '../api/client';
import { canManage, isAdmin, useAuth } from '../auth/AuthContext';
import { date, dateTime, money, statuses } from '../components/display';
import { ErrorBlock, Loading, Notice } from '../components/Feedback';
import RequestNotes from '../components/RequestNotes';
import RequestTags from '../components/RequestTags';
import useLookups from '../components/useLookups';

export default function RequestDetailPage() {
  const { uuid } = useParams();
  const { user } = useAuth();
  const lookups = useLookups();
  const navigate = useNavigate();
  const location = useLocation();
  const [request, setRequest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(location.state?.message || '');
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  function reload() {
    setRevision((n) => n + 1);
  }
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
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
  async function action(operation) {
    if (
      operation === 'permanent' &&
      !window.confirm(
        `Безвозвратно удалить ${request.requestCode}? Заметки также будут удалены. Это действие нельзя отменить.`,
      )
    )
      return;
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api(`/api/requests/${uuid}${operation === 'archive' ? '' : `/${operation}`}`, {
        method: operation === 'restore' ? 'POST' : 'DELETE',
      });
      if (operation === 'permanent')
        navigate('/requests', {
          replace: true,
          state: { message: `Заявка ${request.requestCode} удалена навсегда.` },
        });
      else {
        setNotice(operation === 'archive' ? 'Заявка архивирована.' : 'Заявка восстановлена.');
        reload();
      }
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  if (loading && !request) return <Loading />;
  if (loadError)
    return (
      <>
        <Link to="/requests">← К списку</Link>
        <ErrorBlock error={loadError} onRetry={reload} />
      </>
    );
  if (!request) return null;
  const client = lookups.clients.find((client) => client.id === request.clientId);
  return (
    <>
      <Link className="back-link" to="/requests">
        ← Все заявки
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{request.requestCode}</p>
          <h1>{request.deceasedFullName}</h1>
          <div className="actions">
            <span className={`badge status-${request.status}`}>{statuses[request.status]}</span>
            {request.urgent && <span className="badge urgent">Срочная</span>}
            {request.archived && <span className="badge">Архив</span>}
          </div>
        </div>
        <div className="actions">
          {canManage(user) && (
            <>
              <Link className="button secondary" to={`/requests/${uuid}/edit`}>
                Редактировать
              </Link>
              <button disabled={busy} onClick={() => action(request.archived ? 'restore' : 'archive')}>
                {request.archived ? 'Восстановить' : 'В архив'}
              </button>
            </>
          )}
          {isAdmin(user) && (
            <button className="danger" disabled={busy} onClick={() => action('permanent')}>
              Удалить навсегда
            </button>
          )}
        </div>
      </div>
      <Notice>{notice}</Notice>
      <ErrorBlock error={error} />
      <ErrorBlock error={lookups.error} onRetry={lookups.reload} />
      <section className="card">
        <h2>Данные заявки</h2>
        <dl className="detail-grid">
          <div>
            <dt>Клиент</dt>
            <dd>{client?.fullName || 'Загружаем…'}</dd>
            {client && (
              <small className="muted">
                {client.phone}
                {client.email ? ` · ${client.email}` : ''}
              </small>
            )}
          </div>
          <div>
            <dt>Дата церемонии</dt>
            <dd>{date(request.ceremonyDate)}</dd>
          </div>
          <div>
            <dt>Тип церемонии</dt>
            <dd>{request.ceremonyType?.name}</dd>
          </div>
          <div>
            <dt>Стоимость</dt>
            <dd>{money(request.price)} ₽</dd>
          </div>
          <div>
            <dt>Приоритет</dt>
            <dd>{request.priority}</dd>
          </div>
          <div>
            <dt>Создана</dt>
            <dd>{dateTime(request.createdAt)}</dd>
          </div>
          <div>
            <dt>Изменена</dt>
            <dd>{dateTime(request.updatedAt)}</dd>
          </div>
        </dl>
        <div className="comment">
          <h3>Комментарий</h3>
          <p className="pre-wrap">{request.comment || 'Комментарий не указан.'}</p>
        </div>
      </section>
      <RequestTags
        request={request}
        tags={lookups.tags}
        editable={canManage(user)}
        onChange={reload}
        onTagsChange={lookups.reload}
      />
      <RequestNotes request={request} editable={canManage(user)} />
    </>
  );
}
