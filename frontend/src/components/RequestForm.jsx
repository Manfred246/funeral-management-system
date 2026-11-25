import { useState } from 'react';
import { statuses } from './display';

export default function RequestForm({ initial, clients, types, busy, onSubmit }) {
  const [clientId, setClientId] = useState(initial?.clientId || '');
  const [typeId, setTypeId] = useState(initial?.ceremonyType?.id || '');
  async function submit(event) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const data = {
      clientId: Number(fields.get('clientId')),
      ceremonyTypeId: Number(fields.get('ceremonyTypeId')),
      deceasedFullName: fields.get('deceasedFullName'),
      ceremonyDate: fields.get('ceremonyDate'),
      price: fields.get('price'),
      comment: fields.get('comment'),
      urgent: fields.has('urgent'),
    };
    if (initial) {
      data.status = fields.get('status');
      data.version = initial.version;
    }
    await onSubmit(data);
  }
  const selectedClient = clients.find((client) => client.id === Number(clientId));
  return (
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <div className="form-grid">
          <label>
            Клиент
            <select name="clientId" required value={clientId} onChange={(e) => setClientId(e.target.value)}>
              <option value="">Выберите клиента</option>
              {clients.map((client) => (
                <option key={client.id} value={client.id}>
                  {client.fullName} · {client.phone}
                </option>
              ))}
            </select>
            {selectedClient && (
              <small className="muted">
                {selectedClient.phone}
                {selectedClient.email ? ` · ${selectedClient.email}` : ''}
              </small>
            )}
          </label>
          <label>
            ФИО умершего
            <input
              name="deceasedFullName"
              defaultValue={initial?.deceasedFullName || ''}
              required
              maxLength={255}
            />
          </label>
          <label>
            Дата церемонии
            <input name="ceremonyDate" type="date" defaultValue={initial?.ceremonyDate || ''} required />
          </label>
          <label>
            Тип церемонии
            <select name="ceremonyTypeId" required value={typeId} onChange={(e) => setTypeId(e.target.value)}>
              <option value="">Выберите тип</option>
              {types.map((type) => (
                <option
                  key={type.id}
                  value={type.id}
                  disabled={!type.enabled && type.id !== initial?.ceremonyType?.id}
                >
                  {type.name}
                  {!type.enabled && ' (отключён)'}
                </option>
              ))}
            </select>
          </label>
          <label>
            Стоимость, ₽
            <input
              name="price"
              type="number"
              min="0"
              step="0.01"
              defaultValue={initial?.price ?? ''}
              required
            />
          </label>
          {initial && (
            <label>
              Статус
              <select name="status" defaultValue={initial.status} required>
                {Object.entries(statuses).map(([key, text]) => (
                  <option key={key} value={key}>
                    {text}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
        <label>
          Комментарий
          <textarea name="comment" rows="4" defaultValue={initial?.comment || ''} />
        </label>
        <label className="checkbox-label">
          <input name="urgent" type="checkbox" defaultChecked={initial?.urgent || false} /> Срочная заявка
        </label>
        <div className="actions">
          <button type="submit">
            {busy ? 'Сохраняем…' : initial ? 'Сохранить изменения' : 'Создать заявку'}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
