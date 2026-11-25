import test from 'node:test';
import assert from 'node:assert/strict';
import { loginDestination } from '../auth/AuthContext.js';

let sequence = 0;

test('both login redirect paths retain the protected URL and enforce required password change', () => {
  const user = { passwordChangeRequired: false };
  const from = '/requests/some-uuid/edit?source=list';
  assert.equal(loginDestination(user, from), from);
  assert.equal(loginDestination({ passwordChangeRequired: true }, from), '/change-password');
  for (const invalid of [undefined, '/login', '//other-host', 'https://other-host']) {
    assert.equal(loginDestination(user, invalid), '/requests');
  }
});
const response = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
const session = {
  accessToken: 'old-access',
  refreshToken: 'opaque-refresh',
  user: { uuid: 'user', roles: ['MANAGER'] },
};

async function client(t, fetcher) {
  const saved = new Map();
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: {
      getItem: (key) => saved.get(key) || null,
      setItem: (key, value) => saved.set(key, value),
      removeItem: (key) => saved.delete(key),
    },
  });
  t.after(() => {
    if (descriptor) Object.defineProperty(globalThis, 'sessionStorage', descriptor);
    else delete globalThis.sessionStorage;
  });
  t.mock.method(globalThis, 'fetch', async (url, options) =>
    url.endsWith('/api/auth/login') ? response(session) : fetcher(url, options),
  );
  const api = await import(`./client.js?test=${++sequence}`);
  await api.loginSession('manager', 'not-a-real-password');
  return api;
}

test('concurrent 401 responses share one refresh and retry with the new access token', async (t) => {
  let refreshes = 0;
  let requests = 0;
  const api = await client(t, async (url, options) => {
    if (url.endsWith('/api/auth/refresh')) {
      refreshes += 1;
      await new Promise((resolve) => setTimeout(resolve, 10));
      assert.equal(JSON.parse(options.body).refreshToken, session.refreshToken);
      return response({ ...session, accessToken: 'new-access' });
    }
    requests += 1;
    return options.headers.get('Authorization') === 'Bearer new-access'
      ? response({ ok: true })
      : response({}, 401);
  });
  assert.deepEqual(await Promise.all([api.api('/api/requests'), api.api('/api/clients')]), [
    { ok: true },
    { ok: true },
  ]);
  assert.equal(refreshes, 1);
  assert.equal(requests, 4);
});

test('a second 401 ends the session without a refresh loop', async (t) => {
  let refreshes = 0;
  let requests = 0;
  const api = await client(t, async (url) => {
    if (url.endsWith('/api/auth/refresh')) {
      refreshes += 1;
      return response({ ...session, accessToken: 'new-access' });
    }
    requests += 1;
    return response({}, 401);
  });
  await assert.rejects(api.api('/api/requests'), (error) => error.status === 401);
  assert.equal(refreshes, 1);
  assert.equal(requests, 2);
  assert.equal(api.hasRefreshSession(), false);
});

test('failed refresh clears stored session and does not retry the original mutation', async (t) => {
  let requests = 0;
  const api = await client(t, async (url) => {
    if (url.endsWith('/api/auth/refresh')) return response({ message: 'Недействительный токен' }, 401);
    requests += 1;
    return response({}, 401);
  });
  await assert.rejects(
    api.api('/api/requests', { method: 'POST', json: {} }),
    (error) => error.status === 401,
  );
  assert.equal(requests, 1);
  assert.equal(api.hasRefreshSession(), false);
  assert.equal(globalThis.sessionStorage.getItem('funeral.refresh'), null);
});

test('backend 400/403/404/409/500 messages and correlation ID reach the caller without refresh', async (t) => {
  for (const status of [400, 403, 404, 409, 500]) {
    const api = await client(t, async (url) => {
      assert.ok(!url.endsWith('/api/auth/refresh'));
      return response({ message: 'Понятная ошибка', correlationId: 'check-id' }, status);
    });
    await assert.rejects(
      api.api('/api/requests'),
      (error) =>
        error.status === status && error.message === 'Понятная ошибка' && error.correlationId === 'check-id',
    );
    t.mock.restoreAll();
  }
});

test('multipart retry preserves file and lets fetch set the boundary', async (t) => {
  const form = new FormData();
  form.set('file', new Blob(['requestCode,name\nA,B']), 'requests.csv');
  form.set('mode', 'CREATE_ONLY');
  const api = await client(t, async (url, options) => {
    if (url.endsWith('/api/auth/refresh')) return response({ ...session, accessToken: 'new-access' });
    assert.equal(options.body, form);
    assert.equal(options.headers.has('Content-Type'), false);
    return options.headers.get('Authorization') === 'Bearer new-access'
      ? response({ validCount: 1 })
      : response({}, 401);
  });
  assert.deepEqual(await api.api('/api/requests/import/preview', { method: 'POST', body: form }), {
    validCount: 1,
  });
});

test('an outstanding refresh cannot restore tokens after logout', async (t) => {
  let complete;
  const api = await client(t, async (url) => {
    if (url.endsWith('/api/auth/refresh'))
      return new Promise((resolve) => {
        complete = resolve;
      });
    if (url.endsWith('/api/auth/logout')) return new Response(null, { status: 204 });
    throw new Error('Unexpected request');
  });
  const refreshing = api.refreshSession();
  await api.logoutSession();
  complete(response({ ...session, accessToken: 'new-access' }));
  await assert.rejects(refreshing, (error) => error.status === 401);
  assert.equal(api.hasRefreshSession(), false);
});

test('password-change-required 403 notifies auth and does not trigger refresh', async (t) => {
  const api = await client(t, async () => response({ message: 'Необходимо сменить временный пароль' }, 403));
  let user;
  api.setSessionListener((value) => {
    user = value;
  });
  await assert.rejects(api.api('/api/requests'), (error) => error.status === 403);
  assert.equal(user.passwordChangeRequired, true);
});

test('query serialization preserves false, zero, commas and search escaping', async (t) => {
  const api = await client(t, async () => response({}));
  const query = new URLSearchParams(
    api.queryString({
      q: 'Иванов & сын',
      urgent: false,
      priority: 0,
      sort: 'price,asc',
      tag: '',
      archived: false,
    }),
  );
  assert.equal(query.get('q'), 'Иванов & сын');
  assert.equal(query.get('urgent'), 'false');
  assert.equal(query.get('priority'), '0');
  assert.equal(query.get('sort'), 'price,asc');
  assert.equal(query.has('tag'), false);
  assert.equal(query.get('archived'), 'false');
});

test('CSV export keeps current filters and sort but omits pagination', async (t) => {
  const api = await client(t, async (url, options) => {
    const params = new URL(url, 'http://frontend.test').searchParams;
    assert.equal(params.has('page'), false);
    assert.equal(params.has('size'), false);
    assert.equal(params.get('q'), 'Иванов & сын');
    assert.equal(params.get('tag'), '7');
    assert.equal(params.get('archived'), 'true');
    assert.equal(params.get('sort'), 'price,asc');
    assert.equal(options.headers.get('Authorization'), 'Bearer old-access');
    return new Response('requestCode,name\nFR-2026-000001,"Имя, фамилия"', {
      headers: { 'Content-Type': 'text/csv' },
    });
  });
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'document');
  let downloaded = false;
  Object.defineProperty(globalThis, 'document', {
    configurable: true,
    value: {
      createElement: () => ({
        click: () => {
          downloaded = true;
        },
      }),
    },
  });
  t.after(() => {
    if (descriptor) Object.defineProperty(globalThis, 'document', descriptor);
    else delete globalThis.document;
  });
  await api.downloadCsv({ q: 'Иванов & сын', tag: 7, archived: true, sort: 'price,asc', page: 3, size: 20 });
  assert.equal(downloaded, true);
});

test('an HTML response does not leak its contents as a JSON parser error', async (t) => {
  const api = await client(t, async () => new Response('<html>private proxy details</html>'));
  await assert.rejects(
    api.api('/api/requests'),
    (error) => error instanceof api.ApiError && !error.message.includes('private proxy details'),
  );
});
