import { useState } from 'react';
import { api } from '../api/client';
import { roleNames } from './display';
import { ErrorBlock, Notice } from './Feedback';

export default function UserCreateForm({ onCreated }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const roles = data.getAll('roles');
    if (!roles.length) {
      setError(new Error('Выберите хотя бы одну роль.'));
      return;
    }
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await api('/api/admin/users', {
        method: 'POST',
        json: {
          login: data.get('login').trim(),
          name: data.get('name').trim(),
          roles,
          temporaryPassword: data.get('temporaryPassword'),
        },
      });
      form.reset();
      onCreated();
      setNotice('Пользователь создан. При первом входе потребуется смена пароля.');
    } catch (error) {
      setError(error);
    } finally {
      form.elements.temporaryPassword.value = '';
      setBusy(false);
    }
  }
  return (
    <details className="card">
      <summary>Создать пользователя</summary>
      <ErrorBlock error={error} />
      <Notice>{notice}</Notice>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <div className="form-grid">
            <label>
              Логин
              <input name="login" required maxLength={100} autoComplete="off" />
            </label>
            <label>
              Имя
              <input name="name" required maxLength={255} />
            </label>
            <label>
              Временный пароль
              <input
                name="temporaryPassword"
                type="password"
                required
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
              />
            </label>
          </div>
          <p>Роли</p>
          <div className="actions">
            {Object.entries(roleNames).map(([role, label]) => (
              <label className="checkbox-label" key={role}>
                <input type="checkbox" name="roles" value={role} defaultChecked={role === 'VIEWER'} />
                {label}
              </label>
            ))}
          </div>
          <button type="submit">{busy ? 'Создаём…' : 'Создать пользователя'}</button>
        </fieldset>
      </form>
    </details>
  );
}
