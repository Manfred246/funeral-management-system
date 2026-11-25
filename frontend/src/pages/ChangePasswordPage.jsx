import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorBlock, Notice } from '../components/Feedback';

export default function ChangePasswordPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    if (data.get('newPassword') !== data.get('confirmation')) {
      setError(new Error('Новый пароль и подтверждение не совпадают.'));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api('/api/auth/change-password', {
        method: 'POST',
        json: {
          currentPassword: data.get('currentPassword'),
          newPassword: data.get('newPassword'),
        },
      });
      try {
        await logout();
      } catch {
        /* Password change already revoked all refresh sessions. */
      }
      navigate('/login', { replace: true, state: { message: 'Пароль изменён. Войдите с новым паролем.' } });
    } catch (error) {
      setError(error);
    } finally {
      form.reset();
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Учётная запись</p>
          <h1>Смена пароля</h1>
        </div>
      </div>
      <section className="card narrow">
        <Notice>
          {user.passwordChangeRequired &&
            'Установлен временный пароль. Чтобы продолжить работу, смените его.'}
        </Notice>
        <p className="muted">После смены пароля потребуется повторный вход.</p>
        <ErrorBlock error={error} />
        <form onSubmit={submit}>
          <fieldset disabled={busy}>
            <label>
              Текущий пароль
              <input name="currentPassword" type="password" required autoComplete="current-password" />
            </label>
            <label>
              Новый пароль
              <input
                name="newPassword"
                type="password"
                required
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
              />
            </label>
            <label>
              Подтверждение нового пароля
              <input name="confirmation" type="password" required autoComplete="new-password" />
            </label>
            <button type="submit">{busy ? 'Сохраняем…' : 'Изменить пароль'}</button>
          </fieldset>
        </form>
      </section>
    </>
  );
}
