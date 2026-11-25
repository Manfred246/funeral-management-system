import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { ErrorBlock, Loading, Notice } from '../components/Feedback';

export default function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  if (auth.loading) return <Loading />;
  if (auth.user)
    return <Navigate to={auth.user.passwordChangeRequired ? '/change-password' : '/requests'} replace />;
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    setBusy(true);
    setError(null);
    try {
      const user = await auth.login(values.get('login').trim(), values.get('password'));
      const from = location.state?.from;
      navigate(
        user.passwordChangeRequired
          ? '/change-password'
          : from?.startsWith('/') && !from.startsWith('//') && from !== '/login'
            ? from
            : '/requests',
        { replace: true },
      );
    } catch (error) {
      setError(error);
    } finally {
      form.elements.password.value = '';
      setBusy(false);
    }
  }
  return (
    <div className="login-page">
      <div className="login-intro">
        <span className="brand-mark">РА</span>
        <p className="eyebrow">Funeral Management System</p>
        <h1>
          Внимание к деталям.
          <br />
          Порядок в работе.
        </h1>
        <p>
          Единое пространство для заявок,
          <br />
          клиентов и ежедневных задач агентства.
        </p>
        <small>Курсовая работа №3 · Web-клиент</small>
      </div>
      <section className="login-form card">
        <p className="eyebrow">Добро пожаловать</p>
        <h2>Вход в систему</h2>
        <p className="muted">Используйте учётную запись, выданную администратором.</p>
        <Notice>{location.state?.message}</Notice>
        <ErrorBlock error={error || auth.sessionError} />
        <form onSubmit={submit}>
          <fieldset disabled={busy}>
            <label>
              Логин
              <input name="login" autoComplete="username" required maxLength={100} autoFocus />
            </label>
            <label>
              Пароль
              <input name="password" type="password" autoComplete="current-password" required />
            </label>
            <button className="full-width" type="submit">
              {busy ? 'Входим…' : 'Войти в систему →'}
            </button>
          </fieldset>
        </form>
        <small className="muted">
          Если доступ заблокирован или пароль утерян, обратитесь к администратору.
        </small>
      </section>
    </div>
  );
}
