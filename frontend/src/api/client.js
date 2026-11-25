const baseUrl = (import.meta.env?.VITE_API_BASE_URL || '').replace(/\/$/, '');
const storageKey = 'funeral.refresh';
let accessToken = null;
let refreshToken = null;
try {
  refreshToken = globalThis.sessionStorage?.getItem(storageKey) || null;
} catch {
  /* Memory-only session still works. */
}
let sessionListener = () => {};
let refreshPromise = null;
let generation = 0;

const messages = {
  400: 'Проверьте введённые данные.',
  401: 'Сессия истекла. Войдите снова.',
  403: 'Недостаточно прав для этой операции.',
  404: 'Запись не найдена.',
  409: 'Операция конфликтует с текущим состоянием записи.',
  500: 'Ошибка сервера. Попробуйте позже.',
};

export class ApiError extends Error {
  constructor(message, status = 0, correlationId = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.correlationId = correlationId;
  }
}

export function setSessionListener(listener) {
  sessionListener = listener;
}
export function hasRefreshSession() {
  return Boolean(refreshToken);
}

function storeRefresh() {
  try {
    if (refreshToken) globalThis.sessionStorage?.setItem(storageKey, refreshToken);
    else globalThis.sessionStorage?.removeItem(storageKey);
  } catch {
    /* Storage can be disabled by the browser. */
  }
}

export function clearSession(reason = null) {
  generation += 1;
  accessToken = null;
  refreshToken = null;
  storeRefresh();
  sessionListener(null, reason);
}

function acceptSession(data) {
  accessToken = data.accessToken;
  refreshToken = data.refreshToken;
  storeRefresh();
  sessionListener(data.user);
}

export async function loginSession(login, password) {
  const data = await api('/api/auth/login', { method: 'POST', json: { login, password }, anonymous: true });
  generation += 1;
  acceptSession(data);
  return data.user;
}

export async function refreshSession() {
  if (refreshPromise) return refreshPromise;
  const started = generation;
  const token = refreshToken;
  if (!token) throw new ApiError(messages[401], 401);
  const pending = (async () => {
    try {
      const data = await api('/api/auth/refresh', {
        method: 'POST',
        json: { refreshToken: token },
        anonymous: true,
      });
      // An outstanding refresh must not restore a session after logout or replace a newer login.
      if (started !== generation) throw new ApiError('Сессия изменилась. Повторите вход.', 401);
      acceptSession(data);
      return data.user;
    } catch (error) {
      if (started === generation) clearSession(error);
      throw error;
    }
  })();
  refreshPromise = pending;
  try {
    return await pending;
  } finally {
    if (refreshPromise === pending) refreshPromise = null;
  }
}

export async function logoutSession() {
  const token = refreshToken;
  clearSession();
  if (token)
    await api('/api/auth/logout', { method: 'POST', json: { refreshToken: token }, anonymous: true });
}

export async function api(path, options = {}) {
  const { json, body, anonymous = false, blob = false, ...fetchOptions } = options;
  const started = generation;
  const sentToken = accessToken;
  async function send() {
    const headers = new Headers(fetchOptions.headers);
    if (json !== undefined) headers.set('Content-Type', 'application/json');
    if (!anonymous && accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
    try {
      return await fetch(`${baseUrl}${path}`, {
        ...fetchOptions,
        headers,
        body: json === undefined ? body : JSON.stringify(json),
      });
    } catch (error) {
      if (error.name === 'AbortError') throw error;
      throw new ApiError('Нет соединения с сервером. Проверьте подключение и адрес backend.');
    }
  }
  let response = await send();
  if (response.status === 401 && !anonymous) {
    if (started !== generation) throw new ApiError(messages[401], 401);
    // Concurrent expired requests share one refresh; a late 401 can reuse its result.
    if (!accessToken || accessToken === sentToken) await refreshSession();
    response = await send();
    if (response.status === 401) clearSession(new ApiError(messages[401], 401));
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    if (!anonymous && response.status === 403 && data.message === 'Необходимо сменить временный пароль') {
      sessionListener({ passwordChangeRequired: true });
    }
    throw new ApiError(
      data.message || messages[response.status] || 'Не удалось выполнить запрос.',
      response.status,
      data.correlationId || response.headers.get('X-Correlation-Id'),
    );
  }
  if (blob) return response.blob();
  if (response.status === 204) return null;
  try {
    return await response.json();
  } catch {
    throw new ApiError('Сервер вернул некорректный ответ. Проверьте адрес API.');
  }
}

export function queryString(values) {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(values)) {
    if (value !== '' && value !== null && value !== undefined) params.set(name, String(value));
  }
  return params.toString();
}

export async function downloadCsv(filters) {
  const { page: _page, size: _size, ...exportFilters } = filters;
  const file = await api(`/api/requests/export/csv?${queryString(exportFilters)}`, { blob: true });
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'funeral-requests.csv';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
