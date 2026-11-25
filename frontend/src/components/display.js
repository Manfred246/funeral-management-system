export const statuses = {
  NEW: 'Новая',
  CONFIRMED: 'Подтверждена',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершена',
  CANCELLED: 'Отменена',
};
export const roleNames = { VIEWER: 'Наблюдатель', MANAGER: 'Менеджер', ADMIN: 'Администратор' };
export function date(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString('ru-RU') : '—';
}
export function dateTime(value) {
  return value ? new Date(value).toLocaleString('ru-RU') : '—';
}
export function money(value) {
  return Number(value ?? 0).toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
