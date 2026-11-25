import { statuses } from './display';

export default function RequestFilters({ values, clients, types, tags, onApply, onReset, disabled }) {
  function submit(event) {
    event.preventDefault();
    onApply(Object.fromEntries(new FormData(event.currentTarget)));
  }
  return (
    <form className="card filters" onSubmit={submit}>
      <fieldset disabled={disabled}>
        <div className="filters-top">
          <label className="search-field">
            Поиск
            <input
              name="q"
              type="search"
              defaultValue={values.q || ''}
              placeholder="Код заявки, ФИО, комментарий…"
            />
          </label>
          <label>
            Заявки
            <select name="archived" defaultValue={values.archived || 'false'}>
              <option value="false">Активные</option>
              <option value="true">Архивные</option>
            </select>
          </label>
          <label>
            Сортировка
            <select name="sort" defaultValue={values.sort || 'createdAt,desc'}>
              <option value="createdAt,desc">Сначала новые</option>
              <option value="createdAt,asc">Сначала старые</option>
              <option value="ceremonyDate,asc">Дата церемонии ↑</option>
              <option value="ceremonyDate,desc">Дата церемонии ↓</option>
              <option value="price,asc">Стоимость ↑</option>
              <option value="price,desc">Стоимость ↓</option>
              <option value="priority,desc">Приоритет ↓</option>
              <option value="requestCode,asc">Код заявки ↑</option>
            </select>
          </label>
        </div>
        <details open>
          <summary>Фильтры</summary>
          <div className="form-grid filters-grid">
            <label>
              Клиент
              <select name="client" defaultValue={values.client || ''}>
                <option value="">Все клиенты</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.fullName}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Тип церемонии
              <select name="ceremonyTypeId" defaultValue={values.ceremonyTypeId || ''}>
                <option value="">Все типы</option>
                {types.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                    {!t.enabled && ' (отключён)'}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Статус
              <select name="status" defaultValue={values.status || ''}>
                <option value="">Все статусы</option>
                {Object.entries(statuses).map(([key, text]) => (
                  <option key={key} value={key}>
                    {text}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Срочность
              <select name="urgent" defaultValue={values.urgent || ''}>
                <option value="">Любая</option>
                <option value="true">Срочные</option>
                <option value="false">Обычные</option>
              </select>
            </label>
            <label>
              Приоритет
              <input
                name="priority"
                type="number"
                min="0"
                step="1"
                defaultValue={values.priority || ''}
                placeholder="Любой"
              />
            </label>
            <label>
              Тег
              <select name="tag" defaultValue={values.tag || ''}>
                <option value="">Все теги</option>
                {tags.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Стоимость от
              <input
                name="minPrice"
                type="number"
                min="0"
                step="0.01"
                defaultValue={values.minPrice || ''}
                placeholder="0,00"
              />
            </label>
            <label>
              Стоимость до
              <input
                name="maxPrice"
                type="number"
                min="0"
                step="0.01"
                defaultValue={values.maxPrice || ''}
                placeholder="Без ограничения"
              />
            </label>
          </div>
        </details>
        <div className="actions">
          <button type="submit">Применить</button>
          <button type="button" className="secondary" onClick={onReset}>
            Сбросить фильтры
          </button>
        </div>
      </fieldset>
    </form>
  );
}
