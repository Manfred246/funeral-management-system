import { Link, Navigate, Route, Routes } from 'react-router';
import RequireAuth from './auth/RequireAuth';
import Layout from './components/Layout';
import AdminUsersPage from './pages/AdminUsersPage';
import AnalyticsPage from './pages/AnalyticsPage';
import AuditPage from './pages/AuditPage';
import CeremonyTypesPage from './pages/CeremonyTypesPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import LoginPage from './pages/LoginPage';
import RequestDetailPage from './pages/RequestDetailPage';
import RequestEditPage from './pages/RequestEditPage';
import RequestListPage from './pages/RequestListPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/requests" replace />} />
          <Route path="requests" element={<RequestListPage />} />
          <Route path="requests/:uuid" element={<RequestDetailPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="ceremony-types" element={<CeremonyTypesPage />} />
          <Route path="change-password" element={<ChangePasswordPage />} />
          <Route element={<RequireAuth roles={['MANAGER', 'ADMIN']} />}>
            <Route path="requests/new" element={<RequestEditPage />} />
            <Route path="requests/:uuid/edit" element={<RequestEditPage />} />
          </Route>
          <Route element={<RequireAuth roles={['ADMIN']} />}>
            <Route path="admin/users" element={<AdminUsersPage />} />
            <Route path="admin/audit" element={<AuditPage />} />
          </Route>
          <Route
            path="*"
            element={
              <section className="card">
                <h1>Страница не найдена</h1>
                <Link to="/requests">Вернуться к заявкам</Link>
              </section>
            }
          />
        </Route>
      </Route>
    </Routes>
  );
}
