import { useRef, useState } from 'react';
import { api } from '../api/client';
import { ErrorBlock } from './Feedback';

export default function CsvImportPanel({ onApplied }) {
  const [file, setFile] = useState(null);
  const [mode, setMode] = useState('CREATE_ONLY');
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [applyUnconfirmed, setApplyUnconfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const applied = useRef(false);
  async function submit(action) {
    if (!file || (action === 'apply' && (!preview || applied.current))) return;
    setBusy(true);
    setError(null);
    const form = new FormData();
    form.set('file', file);
    form.set('mode', mode);
    try {
      const response = await api(`/api/requests/import/${action}`, { method: 'POST', body: form });
      if (action === 'preview') {
        setPreview(response);
        setResult(null);
        applied.current = false;
      } else {
        setResult(response);
        setPreview(null);
        applied.current = true;
        onApplied();
      }
    } catch (error) {
      setError(error);
      // A lost apply response may follow a committed row. Require a new preview before retry.
      if (action === 'apply') {
        setApplyUnconfirmed(true);
        setPreview(null);
        applied.current = true;
        onApplied();
      }
    } finally {
      setBusy(false);
    }
  }
  function resetPreview() {
    setApplyUnconfirmed(false);
    setPreview(null);
    setResult(null);
    setError(null);
    applied.current = false;
  }
  return (
    <details className="card">
      <summary>Импорт заявок из CSV</summary>
      <p className="muted">
        UTF-8, разделитель — запятая. Сначала проверьте файл, затем подтвердите импорт. Корректные строки
        могут быть сохранены отдельно от ошибочных.
      </p>
      <fieldset disabled={busy}>
        <div className="form-grid">
          <label>
            CSV-файл
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => {
                setFile(e.target.files[0] || null);
                resetPreview();
              }}
            />
          </label>
          <label>
            Режим
            <select
              value={mode}
              onChange={(e) => {
                setMode(e.target.value);
                resetPreview();
              }}
            >
              <option value="CREATE_ONLY">Только создание</option>
              <option value="UPSERT_BY_REQUEST_CODE">Создание / обновление по requestCode</option>
            </select>
          </label>
        </div>
        <div className="actions">
          <button type="button" className="secondary" disabled={!file} onClick={() => submit('preview')}>
            {busy ? 'Обрабатываем…' : 'Проверить файл'}
          </button>
          <button
            type="button"
            disabled={!preview || preview.validCount === 0}
            onClick={() => submit('apply')}
          >
            Применить импорт{preview ? ` (${preview.validCount})` : ''}
          </button>
        </div>
      </fieldset>
      <ErrorBlock error={error} />
      {applyUnconfirmed && (
        <div className="message warning" role="status">
          Результат применения не подтверждён. Часть строк могла сохраниться. Проверьте список заявок перед
          повторным импортом, особенно в режиме CREATE_ONLY.
        </div>
      )}
      {preview && (
        <div className="operation-result" role="status">
          <strong>
            Preview: корректных строк {preview.validCount}, ошибок {preview.errorCount}
          </strong>
          <RowErrors errors={preview.errors} />
        </div>
      )}
      {result && (
        <div className="operation-result" role="status">
          <strong>
            Сохранено: {result.successful.length} · Ошибок: {result.errors.length}
          </strong>
          {result.successful.length > 0 && (
            <ul>
              {result.successful.map((item) => (
                <li key={item.line}>
                  Строка {item.line}: {item.requestCode} — {item.created ? 'создана' : 'обновлена'}
                </li>
              ))}
            </ul>
          )}
          <RowErrors errors={result.errors} />
        </div>
      )}
    </details>
  );
}

function RowErrors({ errors }) {
  return (
    errors.length > 0 && (
      <ul className="error-list">
        {errors.map((error, index) => (
          <li key={index}>
            Строка {error.line}: {error.message}
          </li>
        ))}
      </ul>
    )
  );
}
