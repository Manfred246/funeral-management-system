import { useState } from 'react';
import { api } from '../api/client';
import { roleNames } from './display';
import { ErrorBlock, Notice } from './Feedback';

export default function UserEditor({ user, onChanged, onClose }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  async function change(suffix, method, json, message) {
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      const updated = await api(`/api/admin/users/${user.uuid}${suffix}`, { method, json });
      await onChanged(updated);
      setNotice(message);
      return true;
    } catch (error) {
      setError(error);
      return false;
    } finally {
      setBusy(false);
    }
  }
  function edit(event) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    change(
      '',
      'PUT',
      { login: fields.get('login').trim(), name: fields.get('name').trim() },
      'Имя и логин сохранены.',
    );
  }
  function roles(event) {
    event.preventDefault();
    const values = new FormData(event.currentTarget).getAll('roles');
    if (!values.length) {
      setError(new Error('Выберите хотя бы одну роль.'));
      return;
    }
    change('/roles', 'PUT', { roles: values }, 'Роли обновлены.');
  }
  async function reset(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const temporaryPassword = new FormData(form).get('temporaryPassword');
    try {
      await change(
        '/reset-password',
        'POST',
        { temporaryPassword },
        'Пароль сброшен. При следующем входе потребуется его смена.',
      );
    } finally {
      form.reset();
    }
  }
  return (
    <section className="card">
      <div className="section-heading">
        <h2>Пользователь: {user.login}</h2>
        <button className="secondary" disabled={busy} onClick={onClose}>
          Закрыть
        </button>
      </div>
      <ErrorBlock error={error} />
      <Notice>{notice}</Notice>
      <fieldset disabled={busy}>
        <form key={`${user.uuid}-${user.login}-${user.name}`} onSubmit={edit}>
          <div className="form-grid">
            <label>
              Логин
              <input name="login" required maxLength={100} defaultValue={user.login} />
            </label>
            <label>
              Имя
              <input name="name" required maxLength={255} defaultValue={user.name} />
            </label>
          </div>
          <button type="submit">Сохранить данные</button>
        </form>
        <form key={user.roles.join(',')} onSubmit={roles}>
          <h3>Роли</h3>
          <div className="actions">
            {Object.entries(roleNames).map(([role, label]) => (
              <label className="checkbox-label" key={role}>
                <input type="checkbox" name="roles" value={role} defaultChecked={user.roles.includes(role)} />
                {label}
              </label>
            ))}
          </div>
          <button type="submit" className="secondary">
            Сохранить роли
          </button>
        </form>
        <h3>Доступ</h3>
        <p className="muted">
          {user.enabled ? 'Учётная запись включена.' : 'Учётная запись отключена.'} Неудачных входов:{' '}
          {user.failedLoginAttempts}. {user.passwordChangeRequired && 'Требуется смена пароля.'}
        </p>
        <div className="actions">
          <button
            className="secondary"
            onClick={() =>
              change(user.enabled ? '/disable' : '/enable', 'POST', undefined, 'Состояние доступа изменено.')
            }
          >
            {user.enabled ? 'Отключить' : 'Включить'}
          </button>
          <button
            className="secondary"
            onClick={() => change('/unlock', 'POST', undefined, 'Счётчик неудачных входов сброшен.')}
          >
            Разблокировать
          </button>
        </div>
        <h3>Сброс пароля</h3>
        <form onSubmit={reset}>
          <div className="actions">
            <label>
              Новый временный пароль
              <input
                name="temporaryPassword"
                type="password"
                required
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
              />
            </label>
            <button className="secondary" type="submit">
              Сбросить пароль
            </button>
          </div>
        </form>
      </fieldset>
    </section>
  );
}
