# Funeral Management System

Учебная система для оформления и обработки заявок ритуального агентства.

Проект состоит из двух Maven-модулей:

- `backend` — REST API на Spring Boot;
- `console-client` — отдельный консольный клиент.

Данные хранятся в PostgreSQL. Консольный клиент работает только через REST API:

```text
Console Client → REST Controller → Service → JDBC Repository → PostgreSQL
```

## Требования для запуска

- JDK 21;
- Maven;
- PostgreSQL;
- утилиты `createdb` и `psql` в `PATH` либо DBeaver.

## Создание базы данных

Создайте пустую базу `funeral_management` и выполните скрипты из корня проекта:

```powershell
createdb -h localhost -p 5432 -U postgres -W funeral_management
psql -h localhost -p 5432 -U postgres -W -d funeral_management -v ON_ERROR_STOP=1 -f database/schema.sql
psql -h localhost -p 5432 -U postgres -W -d funeral_management -v ON_ERROR_STOP=1 -f database/seed.sql
```

В DBeaver эти же файлы можно открыть в SQL-редакторе и выполнить по порядку.

- `schema.sql` создаёт таблицы и связи;
- `seed.sql` добавляет 6 клиентов и 12 заявок;
- `drop.sql` удаляет таблицы и предназначен только для тестовой или пустой БД.

Основная связь:

```text
clients.id ← funeral_requests.client_id
Client 1 → N FuneralRequest
```

Справочники `ceremony_types` и `request_statuses` связаны с заявками внешними
ключами. Схема использует `PRIMARY KEY`, `FOREIGN KEY`, `NOT NULL`, `UNIQUE` и
`CHECK`.

## Настройка backend

Backend использует следующие переменные окружения:

- `DB_URL` — JDBC URL, по умолчанию `jdbc:postgresql://localhost:5432/funeral_management`;
- `DB_USERNAME` — пользователь, по умолчанию `postgres`;
- `DB_PASSWORD` — пароль PostgreSQL.

Пример запуска в PowerShell без сохранения пароля в проекте:

```powershell
$databaseCredential = Get-Credential -UserName postgres -Message 'Пароль PostgreSQL'
$env:DB_USERNAME = $databaseCredential.UserName
$env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
mvn -pl backend spring-boot:run
```

Backend не создаёт и не очищает таблицы автоматически.

## Запуск консольного клиента

Сначала запустите backend. Затем в другом терминале выполните:

```powershell
mvn -pl console-client package
java -jar console-client/target/console-client-1.0-SNAPSHOT.jar
```

По умолчанию клиент обращается к `http://localhost:8080`. Другой адрес задаётся
переменной `FUNERAL_API_URL`:

```powershell
$env:FUNERAL_API_URL = 'http://localhost:8080'
java -jar console-client/target/console-client-1.0-SNAPSHOT.jar
```

В меню доступны:

- CRUD клиентов;
- CRUD заявок;
- поиск по части ФИО умершего и по клиенту;
- фильтрация по статусу, типу церемонии и диапазону дат;
- сортировка по дате и стоимости;
- статистика;
- экспорт заявок в `exports/funeral-requests.xlsx`.

## REST API

### Клиенты

```text
POST   /api/clients
GET    /api/clients
GET    /api/clients/{id}
PUT    /api/clients/{id}
DELETE /api/clients/{id}
```

### Заявки

```text
POST   /api/requests
GET    /api/requests
GET    /api/requests/{id}
PUT    /api/requests/{id}
DELETE /api/requests/{id}
```

Дополнительные запросы:

| Операция | Пример |
|---|---|
| Поиск по ФИО | `/api/requests/search?deceasedName=Иванов` |
| Поиск по клиенту | `/api/requests/search?clientId=2` |
| Фильтрация | `/api/requests/filter?status=NEW&ceremonyType=BURIAL` |
| Фильтрация по датам | `/api/requests/filter?dateFrom=2026-10-01&dateTo=2026-10-31` |
| Сортировка по цене | `/api/requests/sort?field=price&ascending=false` |
| Сортировка по дате | `/api/requests/sort?field=ceremonyDate&ascending=true` |
| Статистика | `/api/statistics` |
| Экспорт XLSX | `/api/requests/export` |

Пример создания заявки:

```json
{
  "clientId": 1,
  "deceasedFullName": "Иванов Иван Петрович",
  "ceremonyDate": "2026-10-15",
  "ceremonyType": "BURIAL",
  "price": 45000.00,
  "comment": null
}
```

## Бизнес-правила

- новая заявка создаётся со статусом `NEW`;
- заявка относится только к существующему клиенту;
- дата новой церемонии не может находиться в прошлом;
- стоимость не может быть отрицательной;
- запрещены недопустимые переходы статусов;
- нельзя удалить клиента со связанными заявками;
- телефон и email клиента должны быть уникальными.

Enum `CeremonyType` и `RequestStatus` используют явные `databaseId`. Для связи с
БД не применяется `ordinal()`.

## Обработка ошибок

REST API возвращает единый JSON-ответ без SQL и stack trace:

- `400 Bad Request` — неправильные входные данные;
- `404 Not Found` — запись не найдена;
- `409 Conflict` — нарушение бизнес-правила или уникальности;
- `500 Internal Server Error` — ошибка БД или сервера.

Консольный клиент обрабатывает неверные числа, даты, цены, статусы, ошибки API и
недоступность backend без аварийного завершения.

## Тесты и сборка

Обычная проверка не требует запущенной PostgreSQL:

```powershell
mvn clean test
mvn clean install
```

Интеграционные тесты используют отдельные временные схемы PostgreSQL и не
изменяют таблицы `public`:

```powershell
$databaseCredential = Get-Credential -UserName postgres -Message 'Пароль PostgreSQL для тестов'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/funeral_management'
$env:TEST_DB_USERNAME = $databaseCredential.UserName
$env:TEST_DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
try {
    mvn -Ppostgres-it clean install
} finally {
    Remove-Item Env:TEST_DB_PASSWORD
}
```
