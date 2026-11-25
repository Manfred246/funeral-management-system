import { useEffect, useState } from 'react';
import { api, queryString } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { roleNames } from '../components/display';
import { Empty, ErrorBlock, Loading } from '../components/Feedback';
import Pagination from '../components/Pagination';
import UserCreateForm from '../components/UserCreateForm';
import UserEditor from '../components/UserEditor';

export default function AdminUsersPage() {
  const { user: currentUser, reloadUser } = useAuth();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  function reload() {
    setRevision((n) => n + 1);
  }
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    api(`/api/admin/users?${queryString({ page, size })}`, { signal: controller.signal })
      .then(setData)
      .catch((error) => {
        if (error.name !== 'AbortError') setError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, size, revision]);
  async function changed(updated) {
    setSelected(updated);
    reload();
    if (updated.uuid === currentUser.uuid) await reloadUser();
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Администрирование</p>
          <h1>Пользователи</h1>
          <p className="muted">Доступ, роли и временные пароли сотрудников.</p>
        </div>
      </div>
      <UserCreateForm onCreated={reload} />
      <section className="card table-card">
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorBlock error={error} onRetry={reload} />
        ) : (
          data && (
            <>
              {data.content.length ? (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Сотрудник</th>
                        <th>Логин</th>
                        <th>Роли</th>
                        <th>Доступ</th>
                        <th>Пароль / входы</th>
                        <th>Действия</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.content.map((user) => (
                        <tr key={user.uuid}>
                          <td>
                            <strong>{user.name}</strong>
                          </td>
                          <td>{user.login}</td>
                          <td>{user.roles.map((role) => roleNames[role]).join(', ')}</td>
                          <td>
                            <span className={`badge ${user.enabled ? 'enabled' : ''}`}>
                              {user.enabled ? 'Включён' : 'Отключён'}
                            </span>
                          </td>
                          <td>
                            {user.passwordChangeRequired ? 'Требуется смена' : 'Постоянный пароль'}
                            <small className="cell-title muted">
                              Неудачных входов: {user.failedLoginAttempts}
                            </small>
                          </td>
                          <td>
                            <button className="secondary" onClick={() => setSelected(user)}>
                              Управление
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty />
              )}
              <Pagination
                {...data}
                disabled={loading}
                onPage={setPage}
                onSize={(size) => {
                  setSize(size);
                  setPage(0);
                }}
              />
            </>
          )
        )}
      </section>
      {selected && (
        <UserEditor
          key={selected.uuid}
          user={selected}
          onChanged={changed}
          onClose={() => setSelected(null)}
        />
      )}
    </>
  );
}
