import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { api, downloadCsv, queryString } from '../api/client';
import { canManage, useAuth } from '../auth/AuthContext';
import BulkPanel from '../components/BulkPanel';
import CsvImportPanel from '../components/CsvImportPanel';
import { date, money, statuses } from '../components/display';
import { Empty, ErrorBlock, Loading, Notice } from '../components/Feedback';
import Pagination from '../components/Pagination';
import RequestFilters from '../components/RequestFilters';
import useLookups from '../components/useLookups';

const filterNames = [
  'q',
  'client',
  'ceremonyTypeId',
  'status',
  'urgent',
  'priority',
  'archived',
  'minPrice',
  'maxPrice',
  'tag',
  'sort',
  'page',
  'size',
];

export default function RequestListPage() {
  const { user } = useAuth();
  const location = useLocation();
  const editable = canManage(user);
  const lookups = useLookups();
  const [params, setParams] = useSearchParams();
  const filters = { archived: 'false', sort: 'createdAt,desc', page: '0', size: '20' };
  for (const key of filterNames) {
    if (params.has(key)) filters[key] = params.get(key);
  }
  const query = queryString(filters);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [exportError, setExportError] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [selected, setSelected] = useState([]);
  const [revision, setRevision] = useState(0);
  function reload() {
    setRevision((value) => value + 1);
  }

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    setSelected([]);
    api(`/api/requests?${query}`, { signal: controller.signal })
      .then(setData)
      .catch((error) => {
        if (error.name !== 'AbortError') setError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query, revision]);

  function changeFilters(values) {
    setParams(queryString({ ...filters, ...values }));
  }
  async function exportFile() {
    setExportError(null);
    setExporting(true);
    try {
      await downloadCsv(filters);
    } catch (error) {
      setExportError(error);
    } finally {
      setExporting(false);
    }
  }
  const rows = loading || error ? [] : data?.content || [];
  function toggle(uuid) {
    setSelected((previous) =>
      previous.includes(uuid) ? previous.filter((id) => id !== uuid) : [...previous, uuid],
    );
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Рабочее пространство</p>
          <h1>
            Заявки <span className="heading-count">{data?.totalElements ?? '—'}</span>
          </h1>
          <p className="muted">Учёт церемоний и работа с обращениями клиентов</p>
        </div>
        <div className="actions">
          <button className="secondary" onClick={exportFile} disabled={exporting || loading}>
            {exporting ? 'Экспортируем…' : '↓ Экспорт CSV'}
          </button>
          {editable && (
            <Link className="button" to="/requests/new">
              + Новая заявка
            </Link>
          )}
        </div>
      </div>
      <Notice>{location.state?.message}</Notice>
      <ErrorBlock error={exportError} />
      <ErrorBlock error={lookups.error} onRetry={lookups.reload} />
      <RequestFilters
        key={params.toString()}
        values={filters}
        clients={lookups.clients}
        types={lookups.types}
        tags={lookups.tags}
        disabled={loading || lookups.loading}
        onApply={(values) => changeFilters({ ...values, page: 0 })}
        onReset={() => setParams({})}
      />
      <section className="card table-card">
        <div className="section-heading">
          <h2>{filters.archived === 'true' ? 'Архив заявок' : 'Активные заявки'}</h2>
          <span className="muted">Данные и фильтры — на сервере</span>
        </div>
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorBlock error={error} onRetry={reload} />
        ) : (
          <>
            {rows.length === 0 ? (
              <Empty>По выбранным условиям заявок нет. Измените фильтры или создайте заявку.</Empty>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      {editable && (
                        <th>
                          <input
                            type="checkbox"
                            aria-label="Выбрать текущую страницу"
                            checked={rows.length > 0 && rows.every((row) => selected.includes(row.uuid))}
                            onChange={(e) => setSelected(e.target.checked ? rows.map((row) => row.uuid) : [])}
                          />
                        </th>
                      )}
                      <th>Заявка / умерший</th>
                      <th>Клиент</th>
                      <th>Церемония</th>
                      <th>Статус</th>
                      <th className="numeric">Стоимость, ₽</th>
                      <th>Приоритет</th>
                      <th>Архив</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr key={row.uuid} className={selected.includes(row.uuid) ? 'selected-row' : ''}>
                        {editable && (
                          <td>
                            <input
                              type="checkbox"
                              aria-label={`Выбрать ${row.requestCode}`}
                              checked={selected.includes(row.uuid)}
                              onChange={() => toggle(row.uuid)}
                            />
                          </td>
                        )}
                        <td>
                          <Link className="request-code" to={`/requests/${row.uuid}`}>
                            {row.requestCode}
                          </Link>
                          <strong className="cell-title">{row.deceasedFullName}</strong>
                          {row.urgent && <span className="badge urgent">Срочная</span>}
                        </td>
                        <td>
                          {lookups.clients.find((client) => client.id === row.clientId)?.fullName ||
                            'Клиент недоступен'}
                        </td>
                        <td>
                          <strong className="cell-title">{date(row.ceremonyDate)}</strong>
                          <span className="muted">{row.ceremonyType?.name}</span>
                        </td>
                        <td>
                          <span className={`badge status-${row.status}`}>
                            {statuses[row.status] || row.status}
                          </span>
                        </td>
                        <td className="numeric">{money(row.price)}</td>
                        <td>
                          <span className="priority">{row.priority}</span>
                        </td>
                        <td>{row.archived ? 'Да' : 'Нет'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {data && (
              <Pagination
                {...data}
                onPage={(page) => changeFilters({ page })}
                onSize={(size) => changeFilters({ size, page: 0 })}
                disabled={loading}
              />
            )}
          </>
        )}
      </section>
      {editable && (
        <>
          <BulkPanel
            selected={selected}
            tags={lookups.tags}
            onComplete={reload}
            onClear={() => setSelected([])}
          />
          <CsvImportPanel onApplied={reload} />
        </>
      )}
    </>
  );
}
