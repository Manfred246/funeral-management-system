import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { money, statuses } from '../components/display';
import { Empty, ErrorBlock, Loading } from '../components/Feedback';

export default function AnalyticsPage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    api('/api/analytics/summary', { signal: controller.signal })
      .then(setData)
      .catch((error) => {
        if (error.name !== 'AbortError') setError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [revision]);
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Обзор работы</p>
          <h1>Аналитика</h1>
          <p className="muted">Сводные показатели по всем заявкам, включая архив.</p>
        </div>
        <button className="secondary" disabled={loading} onClick={() => setRevision((n) => n + 1)}>
          Обновить
        </button>
      </div>
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorBlock error={error} onRetry={() => setRevision((n) => n + 1)} />
      ) : (
        data && (
          <>
            <div className="metrics">
              {[
                ['Всего заявок', data.totalRequests],
                ['Активные', data.activeRequests],
                ['Архивные', data.archivedRequests],
                ['Срочные', data.urgentRequests],
                ['Средняя стоимость', `${money(data.averagePrice)} ₽`],
              ].map(([label, value]) => (
                <section className="card metric" key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </section>
              ))}
            </div>
            <div className="analytics-grid">
              <Breakdown
                title="По типу церемонии"
                items={data.byCeremonyType.map((item) => [item.ceremonyTypeId, item.name, item.count])}
              />
              <Breakdown
                title="По статусу"
                items={data.byStatus.map((item) => [
                  item.status,
                  statuses[item.status] || item.status,
                  item.count,
                ])}
              />
              <Breakdown
                title="По приоритету"
                items={data.byPriority.map((item) => [
                  item.priority,
                  `Приоритет ${item.priority}`,
                  item.count,
                ])}
              />
            </div>
          </>
        )
      )}
    </>
  );
}

function Breakdown({ title, items }) {
  return (
    <section className="card">
      <h2>{title}</h2>
      {items.length ? (
        <table>
          <thead>
            <tr>
              <th>Группа</th>
              <th className="numeric">Заявок</th>
            </tr>
          </thead>
          <tbody>
            {items.map(([key, name, count]) => (
              <tr key={key}>
                <td>{name}</td>
                <td className="numeric">{count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <Empty />
      )}
    </section>
  );
}
