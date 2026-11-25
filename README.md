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
Затем `V2__extend_funeral_requests.sql` добавляет новые поля заявок,
а `V3__create_security_tables.sql` создаёт users, user_roles и refresh_sessions.
`V4__manage_ceremony_types.sql` добавляет code/enabled существующему справочнику типов церемонии.
`V5__create_tags.sql` создаёт tags и таблицу связей funeral_request_tags; прежние данные не изменяются.
`V6__create_request_notes.sql` добавляет заметки заявок с автором из users.
`V7__allow_imported_request_codes.sql` сохраняет коды, заменяя generated column на обычную колонку с INSERT/UPDATE-триггером.
`V8__create_audit_events.sql` добавляет audit_events; прежние миграции и данные не изменяются.
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
Затем V3 добавляет таблицы аутентификации, V4 расширяет справочник CeremonyType.
V5 добавляет теги и связи заявок с тегами.
Идентификаторы, названия типов и существующие связи с заявками сохраняются.

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
- `DB_PASSWORD` — пароль PostgreSQL;
- `JWT_SECRET` — обязательный случайный секрет подписи JWT, минимум 32 байта UTF-8;
- `APP_ADMIN_LOGIN`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_NAME` — только для создания первого ADMIN.

Пример запуска в PowerShell без сохранения пароля в проекте:

```powershell
$databaseCredential = Get-Credential -UserName postgres -Message 'Пароль PostgreSQL'
$env:DB_USERNAME = $databaseCredential.UserName
$env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
mvn -pl backend spring-boot:run
```

Flyway управляет созданием и изменением схемы; Hibernate не создаёт и не очищает таблицы.

## Аутентификация и права

Backend использует stateless Spring Security и Bearer JWT. Cookie-сессии, Basic
и form login не используются. CSRF отключён, поскольку авторизация передаётся
явно заголовком, а не автоматически браузерной cookie.

### Секрет и первый ADMIN

Без корректного JWT_SECRET backend не запускается. Секрет не хранится в Git.
Например, в PowerShell можно сгенерировать его и получить bootstrap-пароль без
вывода значений в терминал:

```powershell
$jwtBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$adminCredential = Get-Credential -Message 'Данные первого ADMIN'
$env:APP_ADMIN_LOGIN = $adminCredential.UserName
$env:APP_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
$env:APP_ADMIN_NAME = Read-Host 'Имя первого ADMIN'
```

Для последующих запусков используйте тот же JWT_SECRET через environment или
локальную .env для Compose; новый секрет делает ранее выданные access JWT
недействительными. Compose передаёт JWT_SECRET и APP_ADMIN_* в backend.

Если users пуста и все три APP_ADMIN_* заданы, ApplicationRunner создаёт первого
ADMIN с BCrypt-хэшем, enabled=true и passwordChangeRequired=true. Если users уже
не пуста, bootstrap её не изменяет. Без APP_ADMIN_* пользователь по умолчанию не
создаётся; частично заданная конфигурация при пустой users приводит к понятной
ошибке запуска. После создания администратора bootstrap-пароль можно убрать из
окружения. Стандартный пользователь Spring Boot отключён.

Пароль при создании и смене: минимум 8 символов, максимум 72 байта UTF-8
(ограничение BCrypt). Пароли и passwordHash не возвращаются в API и не логируются.
Логины чувствительны к регистру; пробелы по краям логина убираются.

### Auth endpoints

| Endpoint | Body | Результат |
|---|---|---|
| POST `/api/auth/login` | `login`, `password` | Tokens и безопасный user DTO |
| POST `/api/auth/refresh` | `refreshToken` | Новый access token, прежний refresh token |
| POST `/api/auth/logout` | `refreshToken` | 204, серверная refresh session отозвана |
| GET `/api/auth/me` | — | UUID, login, name, roles, enabled, passwordChangeRequired |
| POST `/api/auth/change-password` | `currentPassword`, `newPassword` | 204, пароль изменён |

Login/refresh/logout не требуют access JWT. Logout подтверждается самим refresh
token; повторный logout, включая неизвестный token, возвращает 204.
Me и change-password требуют `Authorization: Bearer <accessToken>`.

Успешный login/refresh возвращает поля:

```text
accessToken
refreshToken
tokenType = Bearer
expiresIn = 900 (секунды)
user { uuid, login, name, roles, enabled, passwordChangeRequired }
```

Access token — HS256 JWT на 15 минут; claims: sub (UUID), login, roles, iat, exp.
Подпись, срок действия, обязательные claims и алгоритм проверяются. При каждом
защищённом запросе enabled, roles и требование смены пароля берутся из БД.
Отключённый пользователь получает 401 даже с ранее действительным JWT.

Refresh token — случайные 32 байта в Base64URL, срок 7 дней, без rotation.
В refresh_sessions хранится только SHA-256-хэш токена, связь с User, expiresAt,
revoked и createdAt. Refresh проверяет существование, expiry, revoked и enabled.
Auth timestamps хранятся в UTC. Смена пароля отзывает все refresh sessions,
сбрасывает failedLoginAttempts и passwordChangeRequired. После неё следует войти
заново. Logout и смена пароля не blacklist-ят access JWT: он истекает через 15 минут.

Пять последовательных неправильных паролей блокируют последующие login, включая
правильный пароль. Успешный вход до достижения порога обнуляет счётчик. Счётчик
сохраняется, несмотря на ответ 401, и защищён блокировкой строки при login.
Автоматического таймера разблокировки нет. ADMIN может выполнить reset/unlock через
`/api/admin/users`; пользователь с ещё действующим access token может подтвердить
текущий пароль и сменить его, что также сбросит счётчик.

Пока passwordChangeRequired=true, разрешены login, refresh, logout, me и смена
пароля. Бизнес-API отвечает 403 до успешной смены временного пароля.

### Матрица прав

| Действие | VIEWER | MANAGER | ADMIN |
|---|---|---|---|
| GET заявок, legacy-поиск, XLSX export | Да | Да | Да |
| GET статистики и клиентов | Да | Да | Да |
| Создание/изменение/архивирование/восстановление заявок | Нет | Да | Да |
| Физическое удаление заявки | Нет | Нет | Да |
| Создание/изменение/удаление Client с прежними бизнес-правилами | Нет | Да | Да |
| GET справочника CeremonyType | Да | Да | Да |
| Создание/изменение/enable/disable CeremonyType | Нет | Нет | Да |
| Me и смена собственного пароля | Да | Да | Да |

Без действительного access token защищённые endpoints возвращают 401; при
недостаточной роли — 403. Ошибки используют прежнюю JSON-структуру ApiErrorResponse,
без JWT internals, SQL, passwordHash и stack trace. Неверные credentials, отключённый
или заблокированный login дают одинаковый ответ 401. Некорректная структура body
возвращает 400. Неизвестные endpoints запрещены по умолчанию.

## Запуск консольного клиента

JavaFX и console-client пока не умеют получать/обновлять JWT. После подключения
Security их запросы без Bearer token возвращают 401; адаптация клиентов будет
отдельной задачей. Ниже сохранены команды сборки и запуска.

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

### Типы церемонии

```text
GET  /api/ceremony-types
GET  /api/ceremony-types/{id}
POST /api/ceremony-types
PUT  /api/ceremony-types/{id}
POST /api/ceremony-types/{id}/enable
POST /api/ceremony-types/{id}/disable
```

GET разрешён VIEWER/MANAGER/ADMIN и возвращает также отключённые типы.
Изменения доступны только ADMIN. POST создаёт включённый тип и возвращает 201
с Location; PUT/enable/disable возвращают 200 с DTO. Physical DELETE отсутствует.

Создание принимает `code` и `name`; update — только `name`. Code нормализуется
через trim + uppercase, уникален и после создания не меняется. ID не принимается
из body. Имя обрезается по краям. Максимумы: code — 50 символов, name — 100.
Пустые/слишком длинные значения дают 400; дубликат code — 409; неизвестный ID — 404.
Исходное ограничение уникальности name в БД также сохранено.

V4 сохраняет ID 1/2 и прежние имена: BURIAL / Захоронение, CREMATION / Кремация.
Для дополнительных старых строк код — LEGACY_<id>. Все старые типы изначально
enabled=true. Java Long id отображается на исходный PostgreSQL INTEGER через
JdbcTypeCode; PK, FK и ceremony_type_id не изменены. Допустимый ID — от 1 до 2147483647.

Disable разрешён и для используемого типа: заявки не удаляются и не меняют тип.
Создание заявки или назначение другого disabled-типа отклоняется с 409.
Чтение существующей заявки разрешено. При update с прежним ceremonyTypeId
отключение типа не препятствует изменению остальных полей.

### Заявки

```text
POST   /api/requests
GET    /api/requests
GET    /api/requests/{uuid}
PUT    /api/requests/{uuid}
DELETE /api/requests/{uuid}
POST   /api/requests/{uuid}/restore
DELETE /api/requests/{uuid}/permanent
```

Публичные URL заявок используют UUID. POST возвращает Location с UUID созданной заявки.
Заявка сохраняет внутренний `Long id`; поле `id` пока остаётся в response DTO
для отображения в старых клиентах. JavaFX и console-client ещё используют Long-id URL
и требуют отдельной адаптации операций просмотра/редактирования/удаления и передачи version.
Временных Long-id endpoints нет. Будущий web-клиент должен использовать UUID и requestCode.
В ответ добавлены `uuid`, `requestCode`, `urgent`, `priority`, `archived`, `updatedAt`,
`version`. Create/update принимают обязательный `ceremonyTypeId`, а не enum code.
Service загружает тип из справочника, mapper в БД не обращается. В response
ceremonyType — DTO-объект, например:

```json
{
  "ceremonyType": {
    "id": 1,
    "code": "BURIAL",
    "name": "Захоронение",
    "enabled": true
  }
}
```

Legacy поля ceremonyType с enum-строкой в create/update нет. JavaFX/console-client
потребуют адаптации этого DTO отдельно.

Create принимает дополнительный `urgent` (по умолчанию false).
При Update отсутствие `urgent` сохраняет прежнее значение; явное false отключает срочность.
Update требует `version`, полученную при последнем чтении заявки. Например:

```json
{
  "clientId": 1,
  "deceasedFullName": "Иванов Иван Петрович",
  "ceremonyDate": "2027-01-15",
  "ceremonyTypeId": 1,
  "status": "CONFIRMED",
  "price": 45000.00,
  "version": 0
}
```

Отсутствующая/null или отрицательная version возвращает 400. Устаревшая version
возвращает 409. Ответ успешного PUT содержит актуальную version после сохранения.
Create не принимает version; mapper не копирует входную version в entity.
Остальные новые поля назначаются сервером и не принимаются из тела запроса.

Обычный DELETE архивирует заявку (`archived=true`) и возвращает 204, строка остаётся
в БД и доступна через GET по UUID. Restore устанавливает `archived=false` и возвращает
200 с DTO. Оба действия обновляют updatedAt; Hibernate увеличивает @Version.
Повторное архивирование/восстановление идемпотентно: данные, updatedAt и version
не меняются, если состояние уже нужное. Permanent DELETE физически удаляет строку
и возвращает 204; дальнейший GET возвращает 404.

GET /api/requests возвращает страницу заявок, по умолчанию только активные.
XLSX export по-прежнему выгружает весь активный список без пагинации.
Legacy search/filter/sort также исключают архивные заявки. Статистика сохраняет
прежнее поведение и учитывает все записи.

#### Поиск, фильтры и пагинация

Основной endpoint списка — `GET /api/requests`:

| Параметр | Значение |
|---|---|
| `q` | Подстрока ФИО умершего, requestCode или имени клиента, без учёта регистра |
| `client` | Существующий Long ID клиента |
| `ceremonyTypeId` | Long ID типа из `/api/ceremony-types`; disabled-типы также доступны для поиска |
| `status` | Значение RequestStatus |
| `urgent` | `true`/`false`; отсутствие не фильтрует |
| `priority` | Сохранённое неотрицательное значение priority |
| `archived` | По умолчанию `false`; `true` возвращает только архивные |
| `minPrice`, `maxPrice` | Включительные границы стоимости, неотрицательные; min <= max |
| `page` | Номер страницы с 0, по умолчанию 0 |
| `size` | От 1 до 100, по умолчанию 20; превышение максимума возвращает 400 |
| `sort` | Одно поле и направление, по умолчанию `createdAt,desc` |

Разрешены sort fields: `createdAt`, `ceremonyDate`, `price`, `priority`, `requestCode`.
Направление — `asc` или `desc`. При равных значениях добавляется сортировка по
внутреннему ID для устойчивого порядка страниц. Остальные поля/направления запрещены.
Фильтры объединяются через AND; внутри q три текстовых поля объединены через OR.
Пустой q не фильтрует. `%`, `_` и `!` в поиске означают буквальные символы.
Некорректные числа, enum, границы, номер/размер страницы и sort возвращают 400.

Примеры:

```text
GET /api/requests?q=Иванов&client=1&status=NEW&page=0&size=20&sort=price,asc
GET /api/requests?urgent=true&priority=4&minPrice=10000&maxPrice=50000
GET /api/requests?archived=true&sort=createdAt,desc
```

Ответ имеет стабильный DTO-контракт, например для пустого результата:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`content` содержит обычные FuneralRequestResponseDto. Фильтрация, сортировка,
подсчёт и пагинация выполняются в БД через Specifications и Pageable.
JavaFX/console-client пока не адаптированы к page-ответу основного списка.
Legacy `/search`, `/filter`, `/sort` сохраняют прежние параметры и ответ-массив,
используя те же Specifications. Legacy deceasedName ищет только по ФИО умершего;
legacy filter сохраняет dateFrom/dateTo и параметр ceremonyType как code справочника
(например BURIAL или MEMORIAL); внутри используется тот же фильтр relationship по ID. Новым клиентам следует использовать
основной GET /api/requests.

Для новой заявки `uuid` создаётся через `UUID.randomUUID()`.
Код имеет формат `FR-{год createdAt}-{id, минимум 6 цифр}`, например `FR-2026-000015`.
V2 первоначально определяет `request_code` как PostgreSQL stored generated column.
V7 снимает generation expression, сохраняя значения и UNIQUE/NOT NULL.
INSERT-триггер вычисляет код из identity ID и `created_at`, если передан NULL,
или сохраняет явно переданный CSV UPSERT код. Hibernate получает результат через
`@Generated(event = INSERT, writable = true)`. Создание требует одного INSERT и не требует UPDATE для номера.
Существующие строки получают код по тому же правилу. Код имеет ограничения UNIQUE
и NOT NULL; UPDATE-триггер запрещает менять его после создания. При редактировании через API UUID, код
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
| Legacy: поиск по ФИО | `/api/requests/search?deceasedName=Иванов` |
| Legacy: поиск по клиенту | `/api/requests/search?clientId=2` |
| Legacy: фильтрация | `/api/requests/filter?status=NEW&ceremonyType=BURIAL` |
| Legacy: фильтрация по датам | `/api/requests/filter?dateFrom=2026-10-01&dateTo=2026-10-31` |
| Legacy: сортировка по цене | `/api/requests/sort?field=price&ascending=false` |
| Legacy: сортировка по дате | `/api/requests/sort?field=ceremonyDate&ascending=true` |
| Статистика | `/api/statistics` |
| Экспорт XLSX | `/api/requests/export` |

Пример создания заявки:

```json
{
  "clientId": 1,
  "deceasedFullName": "Иванов Иван Петрович",
  "ceremonyDate": "2026-10-15",
  "ceremonyTypeId": 1,
  "price": 45000.00,
  "comment": null
}
```

## Теги заявок

Для всех endpoints нужен `Authorization: Bearer <accessToken>`.

| Endpoint | VIEWER | MANAGER | ADMIN | Результат |
|---|---|---|---|---|
| GET `/api/tags` | Да | Да | Да | Все теги, по ID |
| GET `/api/tags/{id}` | Да | Да | Да | Один тег |
| POST `/api/tags` | Нет | Да | Да | 201 и созданный тег |
| GET `/api/requests/{uuid}/tags` | Да | Да | Да | Теги заявки, включая архивную |
| POST `/api/requests/{uuid}/tags/{tagId}` | Нет | Да | Да | 204, добавить связь |
| DELETE `/api/requests/{uuid}/tags/{tagId}` | Нет | Да | Да | 204, удалить только связь |

Создание принимает `{"name":"  VIP  "}`, возвращает `{"id":1,"name":"VIP"}`.
Пробелы по краям удаляются; имя обязательно, максимум 50 символов.
Пустое имя или некорректный ID дают 400, неизвестная заявка или тег — 404.
Повтор имени без учёта регистра даёт 409. Уникальный индекс `lower(name)`
защищает от дублирования и при одновременном создании.
Изменение и удаление самого тега на этом этапе не предусмотрены.

Заявка содержит `tags: [{"id":1,"name":"VIP"}]` в response DTO; без тегов — `[]`.
Связь — owning-side `Set<Tag>`, LAZY ManyToMany без каскадного удаления тегов.
Таблица funeral_request_tags имеет composite PK (funeral_request_id, tag_id),
два FK и индекс tag_id. Permanent delete заявки удаляет её связи, сохраняя tags.

Повторное добавление существующей связи и удаление отсутствующей возвращают 204
без сохранения, изменения updatedAt и version. Оба объекта при этом должны существовать.
Фактическое добавление/удаление обновляет updatedAt; version управляет Hibernate
через существующий @Version. Конфликт параллельных изменений возвращает 409.
UUID, requestCode и createdAt не изменяются. Для архивной заявки чтение разрешено,
любые add/remove возвращают 409; сначала нужно восстановить заявку.

Основной список поддерживает фильтр по ID тега:

```text
GET /api/requests?tag=3&status=NEW&archived=false&page=0&size=20&sort=createdAt,desc
```

Specification выполняет join к tags и distinct в БД. Фильтр сочетается с прежними
параметрами, сортировкой и пагинацией; неизвестный положительный tag ID даёт пустую страницу.
Legacy `/search`, `/filter`, `/sort` не получают отдельного tag-фильтра.

При загрузке страницы EntityGraph включает только client/ceremonyType, а tags
загружаются через `@BatchSize(100)` внутри read-only транзакции до преобразования DTO.
Максимальный размер страницы — 100: коллекции страницы доступны одному batch SELECT,
без отдельного запроса тегов для каждой строки. Collection fetch join с Pageable не используется.
Detail и непагинируемые legacy-запросы загружают tags через отдельный EntityGraph.
Интеграционный тест PostgreSQL проверяет страницу с COUNT и одним batch SELECT тегов.

## Заметки заявок

| Endpoint | Доступ | Результат |
|---|---|---|
| GET `/api/requests/{uuid}/notes` | VIEWER/MANAGER/ADMIN | Массив заметок |
| POST `/api/requests/{uuid}/notes` | MANAGER/ADMIN | 201, DTO и Location |
| PUT `/api/requests/{uuid}/notes/{noteUuid}` | MANAGER/ADMIN | 200, обновлённый DTO |
| DELETE `/api/requests/{uuid}/notes/{noteUuid}` | MANAGER/ADMIN | 204 |

POST и PUT принимают `{"text":"Связаться с клиентом","pinned":true}`.
Текст хранится как обычная строка: пробелы по краям удаляются, пустой текст даёт 400.
`pinned` можно менять; отсутствующее поле означает false.
Список сначала возвращает закреплённые заметки, затем более новые; UUID разрешает равенство времени.
DTO содержит uuid, authorUuid, authorName, text, pinned, createdAt, updatedAt.
Автор определяется authenticated principal при создании, через JSON его назначить нельзя.
Редактирование сохраняет первоначального автора и createdAt; updatedAt обновляется с точностью до микросекунд.
MANAGER/ADMIN могут изменять заметки других авторов.

UUID заметки — её primary key; request_id/author_id ссылаются на существующие
funeral_requests/users. Заметка другого request, неизвестная заметка или заявка дают 404.
Для архивной заявки GET разрешён, POST/PUT/DELETE возвращают 409.
Permanent delete заявки удаляет её заметки, сохраняя пользователей.
Без JWT возвращается 401, при недостаточных правах — 403.

## Массовые операции

`POST /api/requests/bulk` доступен MANAGER/ADMIN с Bearer JWT; VIEWER получает 403.
Поддерживаются только ARCHIVE, RESTORE, CHANGE_STATUS, ADD_TAG, REMOVE_TAG.
Permanent delete в bulk отсутствует.

```json
{
  "operation": "CHANGE_STATUS",
  "uuids": ["8d44b5ce-45e2-487d-81d8-b714004db0d7", "ec742d94-20f4-48c7-a944-e2e21c8b117d"],
  "status": "CONFIRMED"
}
```

Для ADD_TAG/REMOVE_TAG вместо status передаётся положительный tagId.
Для ARCHIVE/RESTORE дополнительные поля не нужны. Отсутствующие или неподходящие
параметры, пустой список UUID, null и некорректные UUID дают 400 до изменений.
Повторяющиеся UUID обрабатываются один раз, в порядке первого появления.

Корректный запрос возвращает HTTP 200, в том числе при частичном успехе:

```json
{
  "successful": ["8d44b5ce-45e2-487d-81d8-b714004db0d7"],
  "errors": [{"uuid": "ec742d94-20f4-48c7-a944-e2e21c8b117d", "message": "Заявка не найдена"}]
}
```

Bulk не имеет общей транзакции: существующие методы FuneralRequestService
открывают транзакцию для каждой заявки. Ошибка, включая конфликт version или сбой
commit, относится к одной записи; остальные продолжают обрабатываться.
Успех учитывается после завершения транзакции. SQL и технические детали в errors не возвращаются.
Version изменяет Hibernate, вручную она не назначается.

Archive/restore и tags используют прежние методы и правила, включая идемпотентность
и запрет изменения тегов архивной заявки. CHANGE_STATUS использует ту же проверку
переходов статусов, что обычное редактирование. Как и в прежнем update,
архивирование само по себе не запрещает смену статуса; для тегов действует отдельный запрет.
Повторное назначение текущего статуса успешно без сохранения и изменения updatedAt/version.

## CSV import/export

Старый XLSX `/api/requests/export` сохранён.

| Endpoint | Права | Вход / ответ |
|---|---|---|
| GET `/api/requests/export/csv` | VIEWER/MANAGER/ADMIN | UTF-8 CSV attachment |
| POST `/api/requests/import/preview` | MANAGER/ADMIN | multipart `file`, `mode`; validCount/errorCount/errors |
| POST `/api/requests/import/apply` | MANAGER/ADMIN | multipart `file`, `mode`; successful/errors |

Export принимает те же фильтры q, client, ceremonyTypeId, status, urgent, priority,
tag, archived, minPrice, maxPrice и ту же разрешённую sort, что основной GET /api/requests.
Используется прежняя Specification и проверка фильтров в Service; отбор выполняет БД.
Экспортируются все подходящие записи без page/size; archived по умолчанию false.

Формат — Apache Commons CSV 1.14.1, RFC 4180: разделитель запятая, кавычка `"`,
удвоение кавычек внутри поля, поддержка запятых и переносов строк в quoted fields.
Кодировка UTF-8; при импорте допустим BOM. Заголовки чувствительны к регистру.

Колонки экспорта:

```text
requestCode,deceasedFullName,client,ceremonyDate,ceremonyType,status,price,urgent,priority,archived,createdAt,comment
```

`client` и `ceremonyType` содержат числовые ID существующих записей.
status — имя enum (например NEW или CONFIRMED), price — десятичное число с точкой,
boolean — true/false, ceremonyDate — ISO yyyy-MM-dd, createdAt — ISO LocalDateTime.
Теги в CSV не включены и импортом не изменяются.

Обязательные колонки и значения импорта: deceasedFullName, client, ceremonyDate,
ceremonyType, status, price. requestCode может отсутствовать или быть пустым только в CREATE_ONLY.
Для UPSERT_BY_REQUEST_CODE он обязателен (1–64 символа, пробелы по краям удаляются).
urgent необязателен: для новой заявки false, для существующей сохраняется при отсутствии колонки.
Если comment отсутствует, комментарий существующей заявки сохраняется; пустой comment очищает его.
priority, archived и createdAt — информационные экспортные поля: при наличии проверяется формат,
но они не присваиваются заявке. Priority пересчитывает Service, архивирование выполняется своим API.
Неизвестные или повторные заголовки и повреждённый CSV отклоняются до обработки любых строк.

Оба import endpoint принимают `mode=CREATE_ONLY` или `mode=UPSERT_BY_REQUEST_CODE`.
Preview только читает БД, формирует отдельные объекты-кандидаты и использует общие
проверки создания/обновления Service. Он не сохраняет заявки, не меняет timestamps,
version или существующие associations. Ошибки содержат line и message; line —
номер физической строки начала записи, заголовок — строка 1.
При повреждённом синтаксисе возвращается одна ошибка файла, validCount=0.
Повтор непустого requestCode в файле делает ошибочными все строки с этим кодом.

CREATE_ONLY создаёт новые заявки; существующий requestCode даёт ошибку строки.
UPSERT ищет по requestCode: найденная заявка обновляется через updateByUuid,
ненайденная создаётся через create. UUID, код и createdAt существующей заявки сохраняются.
Действуют прежние правила Client/CeremonyType, цены, дат и переходов статусов.
Новая заявка обязана иметь status=NEW; другой статус возвращает ошибку.

**UPSERT сохраняет переданный requestCode при создании.** Повтор того же CSV
находит эту запись и обновляет её; uuid, код и createdAt сохраняются.
CREATE_ONLY и обычный POST /api/requests используют серверную генерацию FR-{year}-{id padded}.
В обычном REST CreateDto нет requestCode; Service дополнительно очищает переданный код.
Назначение кода доступно только внутреннему сценарию createFromImport.
Apply возвращает фактические uuid/requestCode и признак created.

Apply повторно валидирует файл и строки; сохранённого preview-состояния на сервере нет.
Каждая строка сохраняется в собственной транзакции. Успех учитывается после commit;
ошибки отдельных строк не отменяют остальные и возвращаются вместе с successful.
Ошибка синтаксиса в конце файла не приводит к записи его начала.
Пример ответа apply:

```json
{
  "successful": [{"line":2,"uuid":"8d44b5ce-45e2-487d-81d8-b714004db0d7","requestCode":"FR-2026-000015","created":true}],
  "errors": [{"line":3,"message":"Стоимость заявки не может быть отрицательной"}]
}
```

Корректно принятый файл, включая ошибки строк, возвращает HTTP 200.
Неверный mode, отсутствующий file или неправильные параметры export дают 400;
без JWT — 401, VIEWER при импорте — 403. SQL и технические детали в errors не возвращаются.

## Аналитика

`GET /api/analytics/summary` доступен VIEWER/MANAGER/ADMIN с Bearer JWT.
Существующий `/api/statistics` сохраняет прежний контракт и реализацию.
Summary учитывает все заявки, включая архивные, без фильтров.

```json
{
  "totalRequests": 1,
  "activeRequests": 1,
  "archivedRequests": 0,
  "urgentRequests": 1,
  "averagePrice": 100.00,
  "byCeremonyType": [{"ceremonyTypeId":1,"code":"BURIAL","name":"Захоронение","count":1}],
  "byStatus": [{"status":"NEW","count":1}],
  "byPriority": [{"priority":4,"count":1}]
}
```

activeRequests — количество с archived=false, независимо от статуса;
archivedRequests — archived=true. urgentRequests учитывает срочные и в архиве.
Средняя цена также учитывает архив: точный SUM(price), делённый на COUNT,
с округлением HALF_UP до двух знаков. Для пустой БД — 0.00.

Используются четыре JPQL aggregate query: общие COUNT/условные SUM/SUM(price),
GROUP BY CeremonyType (ID/code/name), GROUP BY RequestStatus и GROUP BY priority.
Service получает scalar projections, а не FuneralRequest entities.
Группы содержат только встречающиеся значения, в порядке ID типа / статуса / priority.
Отключённый CeremonyType также отображается, если на него есть заявки.
При пустой БД счётчики равны нулю, группировки — пустые массивы.

## Управление пользователями ADMIN

Все `/api/admin/**` доступны только ADMIN; VIEWER/MANAGER получают 403, без JWT — 401.
Для этих endpoints также действует запрет бизнес-запросов до смены временного пароля.

| Endpoint | Назначение |
|---|---|
| GET `/api/admin/users?page=0&size=20` | Список пользователей, максимум 100; порядок login/id |
| POST `/api/admin/users` | Создать пользователя, 201 и безопасный user DTO |
| PUT `/api/admin/users/{uuid}` | Изменить login/name |
| PUT `/api/admin/users/{uuid}/roles` | Назначить непустой набор VIEWER/MANAGER/ADMIN |
| POST `/api/admin/users/{uuid}/enable` | Включить |
| POST `/api/admin/users/{uuid}/disable` | Отключить и отозвать refresh sessions |
| POST `/api/admin/users/{uuid}/reset-password` | Установить временный пароль |
| POST `/api/admin/users/{uuid}/unlock` | Обнулить failedLoginAttempts |

Create принимает `login`, `name`, `roles` и `temporaryPassword`; edit — `login`, `name`;
roles — `{"roles":["MANAGER"]}`; reset — `temporaryPassword`.
Временный пароль задаёт ADMIN во входном запросе. Он не возвращается в ответе и не логируется;
credential DTO скрывают его также в toString. Хранится только BCrypt-хэш.
Общая policy Auth/Admin: минимум 8 символов, максимум 72 байта UTF-8.
Create/reset устанавливают passwordChangeRequired=true; reset дополнительно обнуляет
failedLoginAttempts и отзывает refresh sessions. Unlock не меняет пароль, enabled или roles.
Login/name обрезаются по краям; login уникален с учётом регистра. Конфликт даёт 409.
Ответы содержат uuid, login, name, roles, enabled, passwordChangeRequired, failedLoginAttempts.
Пароля и passwordHash в ответах нет.

Нельзя отключить или лишить роли последнего включённого пользователя с ADMIN.
Изменения roles/enabled сериализуются PESSIMISTIC_WRITE-блокировкой первой строки users;
она стабильна, так как физического удаления пользователей нет. После блокировки
проверяется количество enabled ADMIN. Нарушение возвращает 409.

## Audit и Correlation ID

`GET /api/admin/audit?search=REQUEST&sort=timestamp,desc&page=0&size=20` возвращает
page DTO. Search ищет буквальную подстроку без учёта регистра в eventType, entityType,
description и correlationId. Sort разрешён по timestamp/eventType/entityType,
направления asc/desc; id используется для стабильного порядка. Максимальный size — 100.

AuditEvent содержит Long id, eventType, entityType, entityUuid, userId, timestamp,
description, correlationId. Timestamp хранится в UTC (колонка created_at).
V8 добавляет индексы created_at/id и user_id. Записываются:
LOGIN, REQUEST_CREATED, REQUEST_UPDATED, REQUEST_ARCHIVED, REQUEST_RESTORED,
REQUEST_DELETED, USER_CREATED, PASSWORD_RESET.
Статус и фактические изменения тегов используют REQUEST_UPDATED.
Повторные archive/restore/tag без изменения состояния не создают дубликат события.
Аудит создаётся прямым вызовом AuditService в транзакции бизнес-действия, включая CSV/bulk.
Ошибка аудита откатывает соответствующее бизнес-действие; failed login не создаёт LOGIN.
При LOGIN actor — вошедший пользователь; при бизнес-запросах — текущий authenticated user.
Для bootstrap/действий без HTTP actor может быть null, correlationId генерируется.
Удаление заявки не удаляет её audit events. Описания фиксированы: пароли, хэши,
access/refresh tokens, тексты заявок и тела запросов в аудит не передаются.

Один CorrelationIdFilter выполняется перед Security. Корректный X-Correlation-Id
имеет 1–64 ASCII-символа: `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`.
Отсутствующий или некорректный ID заменяется UUID. ID возвращается в response header
и ErrorResponse (включая 401/403), доступен AuditService и логам через MDC.
В logging pattern добавлен correlationId; HTTP log содержит только method/path/status,
без query/body/Authorization. MDC очищается/восстанавливается в finally.
Tracing framework не используется.

## Бизнес-правила

- новая заявка создаётся со статусом `NEW`;
- заявка относится только к существующему клиенту;
- дата новой церемонии не может находиться в прошлом;
- стоимость не может быть отрицательной;
- запрещены недопустимые переходы статусов;
- нельзя удалить клиента со связанными заявками;
- телефон и email клиента должны быть уникальными.

`RequestStatus` остаётся enum с явными databaseId и прежним AttributeConverter;
ordinal() не используется. CeremonyType — JPA entity, связанная через существующий
ceremony_type_id. Старый CeremonyTypeConverter удалён.

## Обработка ошибок

REST API возвращает единый JSON-ответ без SQL и stack trace:

- `400 Bad Request` — неправильные входные данные;
- `404 Not Found` — запись не найдена;
- `409 Conflict` — нарушение бизнес-правила, уникальности или optimistic locking;
- `500 Internal Server Error` — ошибка БД или сервера.

Консольный клиент обрабатывает неверные числа, даты, цены, статусы, ошибки API и
недоступность backend без аварийного завершения.

При конфликте version, включая гонку записей после предварительной проверки,
backend возвращает 409 с сообщением:
«Запись была изменена другим пользователем. Обновите данные и повторите попытку.»
Клиент должен перечитать заявку и повторить изменение с актуальной version.

## Тесты и сборка

Обычная проверка не требует запущенной PostgreSQL:

```powershell
mvn clean test
mvn clean package
```

Интеграционные тесты требуют реальную PostgreSQL и переменные `TEST_DB_*`.
Они используют отдельные временные схемы, Flyway запускается до Hibernate validate;
таблицы `public` не изменяются. Проверяются V1 на пустой схеме, baseline схемы КР №1
с сохранением данных и применением V2–V8, JPA CRUD, поиск, фильтры, сортировка и текущий REST API.
Для тегов проверяются FK/уникальность, идемпотентность связей, изменение version,
сохранение тегов при удалении заявки, tag-фильтр с пагинацией и batch fetching.
Для заметок проверяются CRUD, автор, принадлежность заявке, архивирование,
FK/уникальность/непустой текст и удаление вместе с заявкой.
Для bulk проверяются все пять операций, partial success, роли, валидация,
ошибки статусов/тегов и транзакционные границы. PostgreSQL-тесты дополнительно
проверяют version и откат только одной записи при ошибке БД.
CSV-тесты покрывают фильтры, quoting/commas/newlines, preview без изменений,
CREATE_ONLY/UPSERT, смешанные результаты и права. PostgreSQL-тесты дополнительно
сверяют snapshot БД/sequence до и после preview, generated code и откат ошибочной строки.
Analytics-тесты проверяют DTO, totals, округление средней цены, группы, доступ всех ролей
и компиляцию JPQL без подключения к БД. PostgreSQL-тесты проверяют реальные агрегаты,
пустую БД, четыре SQL-запроса и отсутствие загрузки entities.
Admin/Audit/Correlation tests проверяют права всех endpoints, BCrypt и reset/unlock,
последнего ADMIN, audit events/search/sort/page, ID из header/генерацию, ErrorResponse,
MDC cleanup и отсутствие секретов в ответах/аудите/HTTP log.
PostgreSQL-сценарии дополнительно проверяют конкурентную защиту последнего ADMIN,
атомарность аудита с бизнес-операцией и сохранение событий после удаления заявки.
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
