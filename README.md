# Funeral Management System

Учебная система для оформления и обработки заявок ритуального агентства.

Проект состоит из двух Maven-модулей:

- `backend` — REST API на Spring Boot;
- `console-client` — отдельный консольный клиент.

Данные хранятся в PostgreSQL. Консольный клиент работает только через REST API:

```text
Console Client → REST Controller → Service → Spring Data JPA Repository → PostgreSQL
```

## Требования для запуска

- JDK 21;
- Maven;
- PostgreSQL;
- утилиты `createdb` и `psql` в `PATH` либо DBeaver.

## Создание базы данных

Создайте пустую базу `funeral_management`:

```powershell
createdb -h localhost -p 5432 -U postgres -W funeral_management
```

При первом запуске backend Flyway выполняет
`backend/src/main/resources/db/migration/V1__baseline_kr1.sql`: создаёт таблицы,
ограничения, индекс и обязательные справочники. Клиенты и заявки не добавляются.
Затем `V2__extend_funeral_requests.sql` добавляет новые поля заявок.
Hibernate работает с `spring.jpa.hibernate.ddl-auto=validate` и только проверяет mapping.

Для демонстрационных данных после первого успешного запуска backend можно выполнить:

```powershell
psql -h localhost -p 5432 -U postgres -W -d funeral_management -v ON_ERROR_STOP=1 -f database/seed.sql
```

`seed.sql` добавляет 6 клиентов и 12 заявок только в пустые бизнес-таблицы.
`database/schema.sql` сохранён как исходная схема КР №1 и для проверки обновления
существующей БД. При обычном новом запуске вручную выполнять его не нужно.
`drop.sql` — отдельный разрушительный учебный скрипт, backend его не запускает.

### Существующая база КР №1

Перед первым запуском проверьте, что `DB_URL` указывает на нужную базу и выбранная
схема соответствует `database/schema.sql`, включая ID справочников и ограничения.
Сохраните резервную копию данных.

Настройки `spring.flyway.baseline-on-migrate=true` и `baseline-version=1` позволяют
принять непустую схему без `flyway_schema_history`: Flyway записывает маркер
`BASELINE` версии 1 и не выполняет V1 повторно. Таблицы и данные сохраняются.
На пустой схеме baseline не создаётся: V1 выполняется как обычная SQL-миграция.
После baseline применяется V2: существующие строки получают UUID, код заявки,
priority, updatedAt и начальную version без изменения прежних полей.

При последующих запусках Flyway читает `flyway_schema_history` и применяет только
новые миграции. Baseline не проверяет все CHECK/FK и данные старой схемы;
Hibernate validate проверяет соответствие отображённых колонок и типов,
поэтому предварительная сверка исходной схемы обязательна.

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

Flyway управляет созданием и изменением схемы; Hibernate не создаёт и не очищает таблицы.

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

Заявка сохраняет внутренний `Long id`; URL и обычный DELETE пока работают как раньше.
В ответ добавлены `uuid`, `requestCode`, `urgent`, `priority`, `archived`, `updatedAt`,
`version`. Create принимает дополнительный `urgent` (по умолчанию false).
При Update отсутствие `urgent` сохраняет прежнее значение; явное false отключает срочность.
Остальные новые поля назначаются сервером и не принимаются из тела запроса.

Для новой заявки `uuid` создаётся через `UUID.randomUUID()`.
Код имеет формат `FR-{год createdAt}-{id, минимум 6 цифр}`, например `FR-2026-000015`.
V2 определяет `request_code` как PostgreSQL stored generated column: БД вычисляет
его из identity ID и `created_at` при INSERT, Hibernate получает результат через
`@Generated`. Создание требует одного INSERT и не требует UPDATE для номера.
Существующие строки получают код по тому же правилу. Код имеет ограничения UNIQUE
и NOT NULL, напрямую записать его нельзя. При редактировании через API UUID, код
и `createdAt` сохраняются.

Priority хранится как результат последнего создания/редактирования:
срочность даёт +2; разница между `createdAt.toLocalDate()` и `ceremonyDate`
от 0 до 3 дней включительно даёт +2, от 4 до 7 — +1, иначе — 0.
Показатель отражает срочность оформления относительно даты церемонии.
Он пересчитывается при сохранении, но не зависит от текущей даты и не устаревает
при неизменных исходных полях.
При создании сервис Java устанавливает одинаковые `createdAt` и `updatedAt`,
при редактировании изменяет только `updatedAt`. Время округляется вниз до микросекунд
в соответствии с точностью PostgreSQL TIMESTAMP. Version управляет Hibernate.

DEFAULT новых колонок и вычисляемый код позволяют выполнять прежний `seed.sql` после V2.
Для прямой вставки SQL priority по умолчанию равен 0; бизнес-расчёт выполняется API.

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
БД не применяется `ordinal()`: JPA AttributeConverter сохраняет явные числовые ID.

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
mvn clean package
```

Интеграционные тесты требуют реальную PostgreSQL и переменные `TEST_DB_*`.
Они используют отдельные временные схемы, Flyway запускается до Hibernate validate;
таблицы `public` не изменяются. Проверяются V1 на пустой схеме, baseline схемы КР №1
с сохранением данных и применением V2, JPA CRUD, поиск, фильтры, сортировка и текущий REST API.
Пользователь тестовой БД должен иметь право создавать и удалять тестовые схемы:

```powershell
$databaseCredential = Get-Credential -UserName postgres -Message 'Пароль PostgreSQL для тестов'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/funeral_management'
$env:TEST_DB_USERNAME = $databaseCredential.UserName
$env:TEST_DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
try {
    mvn -Ppostgres-it clean test
} finally {
    Remove-Item Env:TEST_DB_PASSWORD
}
```

## Docker

Существующий Compose запускает PostgreSQL 17 и backend на Java 21:

```powershell
docker compose build
docker compose up
```

Compose передаёт backend `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` для подключения
к сервису `postgres`. На новом volume существующие init-скрипты PostgreSQL
создают схему КР №1 и демоданные; backend принимает её через Flyway baseline 1.
На существующем volume init-скрипты не выполняются повторно, данные сохраняются.
Новые миграции Flyway применяет при запуске backend.

`docker compose down` сохраняет named volume. Для обновления backend удалять
volume или запускать `down -v` не требуется.
