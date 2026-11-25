export function ErrorBlock({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="message error" role="alert">
      <strong>{error.status ? `Ошибка ${error.status}` : 'Не удалось выполнить операцию'}</strong>
      <p>{error.message || 'Попробуйте ещё раз.'}</p>
      {error.correlationId && (
        <small>
          Код обращения: <code>{error.correlationId}</code>
        </small>
      )}
      {onRetry && (
        <div>
          <button type="button" className="secondary" onClick={onRetry}>
            Повторить
          </button>
        </div>
      )}
    </div>
  );
}

export function Notice({ children }) {
  return children ? (
    <div className="message success" role="status">
      {children}
    </div>
  ) : null;
}

export function Loading() {
  return (
    <div className="empty" role="status">
      Загружаем данные…
    </div>
  );
}
export function Empty({ children = 'Пока нет записей.' }) {
  return <div className="empty">{children}</div>;
}
