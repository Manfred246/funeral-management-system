import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { api, queryString } from '../api/client';
import { dateTime } from '../components/display';
import { Empty, ErrorBlock, Loading } from '../components/Feedback';
import Pagination from '../components/Pagination';

const eventNames = {
  LOGIN: 'Вход',
  REQUEST_CREATED: 'Создание заявки',
  REQUEST_UPDATED: 'Изменение заявки',
  REQUEST_ARCHIVED: 'Архивирование',
  REQUEST_RESTORED: 'Восстановление',
  REQUEST_DELETED: 'Удаление заявки',
  USER_CREATED: 'Создание пользователя',
  PASSWORD_RESET: 'Сброс пароля',
};

export default function AuditPage() {
  const [params, setParams] = useSearchParams();
  const filters = {
    search: params.get('search') || '',
    sort: params.get('sort') || 'timestamp,desc',
    page: params.get('page') || '0',
    size: params.get('size') || '20',
  };
  const query = queryString(filters);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    api(`/api/admin/audit?${query}`, { signal: controller.signal })
      .then(setData)
      .catch((error) => {
        if (error.name !== 'AbortError') setError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query, revision]);
  function update(values) {
    setParams(queryString({ ...filters, ...values }));
  }
  function search(event) {
    event.preventDefault();
    update({ ...Object.fromEntries(new FormData(event.currentTarget)), page: 0 });
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Администрирование</p>
          <h1>Аудит</h1>
          <p className="muted">Важные действия пользователей и изменения заявок.</p>
        </div>
      </div>
      <form key={query} className="card" onSubmit={search}>
        <fieldset className="actions" disabled={loading}>
          <label className="search-field">
            Поиск
            <input
              name="search"
              type="search"
              defaultValue={filters.search}
              placeholder="Событие, описание, correlation ID…"
            />
          </label>
          <label>
            Сортировка
            <select name="sort" defaultValue={filters.sort}>
              <option value="timestamp,desc">Сначала новые</option>
              <option value="timestamp,asc">Сначала старые</option>
              <option value="eventType,asc">По событию</option>
              <option value="entityType,asc">По типу сущности</option>
            </select>
          </label>
          <button type="submit">Найти</button>
          <button type="button" className="secondary" onClick={() => setParams({})}>
            Сбросить
          </button>
        </fieldset>
      </form>
      <section className="card table-card">
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorBlock error={error} onRetry={() => setRevision((n) => n + 1)} />
        ) : (
          data && (
            <>
              {data.content.length ? (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Время</th>
                        <th>Событие</th>
                        <th>Сущность / UUID</th>
                        <th>Пользователь</th>
                        <th>Описание</th>
                        <th>Correlation ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.content.map((event) => (
                        <tr key={event.id}>
                          <td>{dateTime(event.timestamp)}</td>
                          <td>
                            <strong className="cell-title">
                              {eventNames[event.eventType] || event.eventType}
                            </strong>
                            <small className="muted">{event.eventType}</small>
                          </td>
                          <td>
                            {event.entityType}
                            <code className="cell-title break-word">{event.entityUuid}</code>
                          </td>
                          <td>{event.userId ?? 'Система'}</td>
                          <td>{event.description}</td>
                          <td>
                            <code className="break-word">{event.correlationId}</code>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty>По выбранным условиям событий нет.</Empty>
              )}
              <Pagination
                {...data}
                disabled={loading}
                onPage={(page) => update({ page })}
                onSize={(size) => update({ size, page: 0 })}
              />
            </>
          )
        )}
      </section>
    </>
  );
}
