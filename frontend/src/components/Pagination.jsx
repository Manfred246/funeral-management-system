export default function Pagination({ page, size, totalElements, totalPages, onPage, onSize, disabled }) {
  return (
    <div className="pagination">
      <span>
        Всего: <strong>{totalElements}</strong> · Страница {totalPages ? page + 1 : 0} из {totalPages}
      </span>
      <div className="actions">
        <label className="inline">
          На странице{' '}
          <select
            aria-label="Размер страницы"
            value={size}
            disabled={disabled}
            onChange={(e) => onSize(Number(e.target.value))}
          >
            {[10, 20, 50, 100].map((n) => (
              <option key={n}>{n}</option>
            ))}
          </select>
        </label>
        <button className="secondary" disabled={disabled || page <= 0} onClick={() => onPage(page - 1)}>
          ← Назад
        </button>
        <button
          className="secondary"
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          Далее →
        </button>
      </div>
    </div>
  );
}
