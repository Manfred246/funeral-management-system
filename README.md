# Funeral Management System

Учебный проект для оформления и обработки заявок на ритуальные услуги.
Модули: Spring Boot backend и независимый консольный клиент.

## PostgreSQL

Необходимы установленный PostgreSQL и утилиты `createdb`, `psql` в `PATH`.
Из корня проекта создайте отдельную пустую базу и примените схему:

```powershell
createdb -h localhost -p 5432 -U postgres -W funeral_management
psql -h localhost -p 5432 -U postgres -W -d funeral_management -v ON_ERROR_STOP=1 -f database/schema.sql
```

Утилиты запросят пароль интерактивно. Если база уже создана, пропустите `createdb`.
Скрипт схемы выполняется один раз: повторное выполнение завершится ошибкой
существующих таблиц, без удаления данных. Все изменения схемы выполняются
в одной транзакции. Backend автоматически схему не создаёт.

Для создания таблиц через DBeaver откройте `database/schema.sql` в SQL-редакторе
подключения к пустой БД `funeral_management` и выполните весь скрипт.
Файл сохранён в UTF-8; открывайте его в этой кодировке.
Для уже настроенной БД повторно выполнять скрипт не нужно.

### Демонстрационные данные и пересоздание

После создания схемы можно заполнить пустую БД шестью клиентами и двенадцатью
заявками: представлены оба типа церемонии, все пять статусов, разные даты и цены.

```powershell
psql -h localhost -p 5432 -U postgres -W -d funeral_management -v ON_ERROR_STOP=1 -f database/seed.sql
```

`seed.sql` предназначен только для пустых таблиц клиентов и заявок. Повторный
запуск завершится ошибкой без добавления дубликатов. Даты рассчитываются от дня запуска.

`database/drop.sql` удаляет четыре таблицы вместе с данными. Для пересоздания
**тестовой** БД порядок такой: `drop.sql` → `schema.sql` → `seed.sql`.
Для рабочей БД этот цикл не нужен: он уничтожит клиентов и заявки.

### Таблицы и связь

- `clients`: ID, ФИО, уникальный телефон, необязательный уникальный email, дата создания.
- `funeral_requests`: ID, клиент, ФИО умершего, дата и тип церемонии,
  статус, стоимость, комментарий и дата создания.
- `ceremony_types`: `id INTEGER` (первичный ключ с автоинкрементом),
  уникальное русское название `name`.
  Начальные значения: 1 — «Захоронение», 2 — «Кремация».
- `request_statuses`: `id INTEGER` (первичный ключ с автоинкрементом),
  уникальное русское название `name`.
  Начальные значения: 1 — «Новая», 2 — «Подтверждена», 3 — «В процессе»,
  4 — «Завершена», 5 — «Отменена».
- Связь `clients.id → funeral_requests.client_id`: один клиент — много заявок.
  Заявка обязательно относится к существующему клиенту. Удаление клиента
  с заявками запрещено; каскадного удаления заявок нет.
- Связи `ceremony_types.id → funeral_requests.ceremony_type_id` и
  `request_statuses.id → funeral_requests.status_id`: одно значение справочника —
  много заявок. Удаление используемого значения запрещено внешним ключом.
  Оба внешних ключа имеют тип `INTEGER NOT NULL`.

Java enum `CeremonyType` и `RequestStatus` реализованы в предметной модели
с явным числовым полем `databaseId`, не через `ordinal()`:

| Enum | Значение | ID в новой схеме |
|---|---|---|
| `CeremonyType` | `BURIAL` | 1 |
| `CeremonyType` | `CREMATION` | 2 |
| `RequestStatus` | `NEW` | 1 |
| `RequestStatus` | `CONFIRMED` | 2 |
| `RequestStatus` | `IN_PROGRESS` | 3 |
| `RequestStatus` | `COMPLETED` | 4 |
| `RequestStatus` | `CANCELLED` | 5 |

Эти соответствия заданы в `schema.sql` и подтверждены для текущей локальной БД.
Добавление новых значений требует согласованного изменения Java enum.
Переходы статусов справочник не проверяет.

Схема содержит `PRIMARY KEY`, `FOREIGN KEY`, `NOT NULL`, `UNIQUE` и `CHECK`.
Пустые ФИО и телефоны, отрицательная стоимость и неизвестные статусы/типы
церемонии запрещены. Отсутствующий email следует передавать как `NULL`.
ID и время создания формируются базой, статус новой заявки по умолчанию — `NEW`.

Проверка даты новой церемонии и допустимых переходов статуса должна выполняться
в Java Service: исторические заявки могут содержать прошедшую дату.
Сервисы проверяют эти правила при создании и изменении заявок.
Справочники заполняются при создании схемы;
тестовые клиенты и заявки находятся в отдельном `seed.sql`.

Проверить созданные таблицы можно командой:

```powershell
psql -h localhost -p 5432 -U postgres -W -d funeral_management -c '\d clients' -c '\d funeral_requests'
```

### Подключение backend

По умолчанию backend использует `localhost:5432`, базу `funeral_management`
и пользователя `postgres`. Другие адрес и пользователь задаются переменными
`DB_URL` (полный JDBC URL) и `DB_USERNAME`.
Пароль задайте в переменной окружения `DB_PASSWORD` в конфигурации запуска IDE
или в текущем PowerShell без сохранения его в истории команд:

```powershell
$databaseCredential = Get-Credential -UserName postgres -Message 'Пароль PostgreSQL'
$env:DB_USERNAME = $databaseCredential.UserName
$env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
mvn -pl backend spring-boot:run
```

Пароли не следует записывать в файлы, отслеживаемые Git.

## Сборка

Требуются JDK 21 и Maven. Из корня проекта:

```powershell
mvn clean install
```

### Запуск консольного клиента

Сначала запустите backend. Затем в другом терминале соберите и запустите
консольный клиент:

```powershell
mvn -pl console-client package
java -jar console-client/target/console-client-1.0-SNAPSHOT.jar
```

По умолчанию клиент обращается к `http://localhost:8080`. Другой адрес можно
задать переменной окружения `FUNERAL_API_URL`:

```powershell
$env:FUNERAL_API_URL = 'http://localhost:8080'
java -jar console-client/target/console-client-1.0-SNAPSHOT.jar
```

Клиент работает с backend только через REST API. В меню доступны CRUD клиентов
и заявок, поиск, фильтрация, сортировка, статистика и экспорт заявок. XLSX-файл
сохраняется в `exports/funeral-requests.xlsx`.

## Репозитории

Реализованы CRUD клиентов и заявок через прямой JDBC, поиск по ФИО и клиенту,
фильтры по статусу и типу церемонии, сортировка по дате и цене в обе стороны.
SQL-ошибки оборачиваются в `DatabaseException`. Подробности для участников команды
находятся в [контракте репозиториев](docs/repository-contract.md).

Ошибки приложения разделены на четыре понятные категории:
`ValidationException`, `EntityNotFoundException`, `BusinessException` и
`DatabaseException`. HTTP-статусы для них назначает единый `ApiExceptionHandler`.

## Проверка с PostgreSQL

Обычный `mvn clean install` выполняет тесты, которым не нужен сервер БД.
Для интеграционных тестов задайте параметры отдельным набором переменных:

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

URL должен указывать на доступную PostgreSQL БД без параметра `currentSchema`.
Тесты создают случайную схему `repository_test_...`, проверяют выбор именно этой
схемы, выполняют в ней `drop → schema → seed` и удаляют её после проверки.
API-тесты таким же образом используют отдельную схему `api_test_...`.
Пользователю БД нужно право создания схем. Таблицы `public` не изменяются.

Проверяются CRUD, ограничения, поиск, фильтры, сортировки, ошибки подключения,
запуск Spring Boot, REST API и экспорт XLSX. Корневой адрес `/` возвращает 404:
API доступен по адресам ниже.

## REST API

Для клиентов используются `/api/clients`, для заявок — `/api/requests`.
Оба ресурса поддерживают POST (создание), GET (список),
GET `/{id}`, PUT `/{id}` и DELETE `/{id}`.

Дополнительные операции над заявками:

| Операция | Пример GET-запроса |
|---|---|
| Поиск по части ФИО | `/api/requests/search?deceasedName=Иванов` |
| Поиск по клиенту | `/api/requests/search?clientId=2` |
| Фильтры | `/api/requests/filter?status=NEW&ceremonyType=BURIAL` |
| Период включительно | `/api/requests/filter?dateFrom=2026-10-01&dateTo=2026-10-31` |
| Сортировка по цене | `/api/requests/sort?field=price&ascending=false` |
| Сортировка по дате | `/api/requests/sort?field=ceremonyDate&ascending=true` |
| Экспорт всех заявок | `/api/requests/export` |
| Статистика | `/api/statistics` |

Поиск принимает ровно один параметр: `deceasedName` или `clientId`.
Фильтры можно объединять. Сортировка по умолчанию — дата по возрастанию.
Поиск, фильтрация и сортировка выполняются в PostgreSQL.

Пример JSON для создания заявки:

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

Для примера укажите существующего клиента и дату не раньше текущего дня.
При создании статус всегда NEW, ID и время создания возвращаются из БД.
PUT передаёт все изменяемые поля, включая `status`.

Проверки данных выполняются в сервисах. ФИО ограничено 255 символами без
ограничения количества слов. Телефон содержит 10–15 цифр и необязательный `+`;
допустимые пробелы, скобки и дефисы удаляются перед сохранением. Некорректные
символы и порядок скобок отклоняются. Префиксы стран не преобразуются.
Пустой email сохраняется как NULL. Цена — от 0 до 9999999999.99;
значения точнее копейки отклоняются, завершающие нули допустимы (`100.1200`).
Комментарий хранится в TEXT. Если текст превышает лимит ячейки Excel
(32767 символов), экспорт возвращает понятную ошибку 409 без обрезания данных.

Ошибки возвращаются в JSON с полями `timestamp`, `status`, `error`, `message`,
`path`: 400 — неверные данные, 404 — запись не найдена, 409 — повторный
телефон/email, запрещённое удаление клиента или переход статуса,
500 — ошибка БД, формирования файла или другая непредвиденная ошибка сервера.
SQL, технические подробности и stack trace в ответ не попадают.

Статистика содержит общее количество клиентов и заявок, количество новых,
завершённых и отменённых заявок, а также среднюю стоимость заявки.
