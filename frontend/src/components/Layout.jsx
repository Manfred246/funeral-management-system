import { NavLink, Outlet, useNavigate } from 'react-router';
import { isAdmin, useAuth } from '../auth/AuthContext';
import { roleNames } from './display';

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  async function leave() {
    let message = 'Вы вышли из системы.';
    try {
      await logout();
    } catch {
      message = 'Локальная сессия завершена. Сервер недоступен: отзыв refresh-сессии не подтверждён.';
    }
    navigate('/login', { replace: true, state: { message } });
  }
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/requests" className="brand">
          <span className="brand-mark">РА</span>
          <span>
            Ритуальное агентство<small>Управление заявками</small>
          </span>
        </NavLink>
        <div className="nav-label">Рабочее пространство</div>
        <nav aria-label="Основная навигация">
          <NavLink to="/requests">Заявки</NavLink>
          <NavLink to="/analytics">Аналитика</NavLink>
          <NavLink to="/ceremony-types">Типы церемоний</NavLink>
          {isAdmin(user) && (
            <>
              <div className="nav-label">Администрирование</div>
              <NavLink to="/admin/users">Пользователи</NavLink>
              <NavLink to="/admin/audit">Аудит</NavLink>
            </>
          )}
        </nav>
        <div className="sidebar-footer">
          <span>КР №3</span>
          <small>Funeral Management System</small>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="workspace-label">Система учёта</span>
          <div className="user-menu">
            <div>
              <strong>{user.name}</strong>
              <small>{user.roles?.map((role) => roleNames[role] || role).join(', ')}</small>
            </div>
            <NavLink to="/change-password">Сменить пароль</NavLink>
            <button className="secondary" onClick={leave}>
              Выйти
            </button>
          </div>
        </header>
        <main id="main">
          <Outlet />
        </main>
        <footer className="page-footer">Ритуальное агентство · Рабочее пространство</footer>
      </div>
    </div>
  );
}
