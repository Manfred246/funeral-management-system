#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
test_dir="$(mktemp -d)"
server_pid=""

cleanup() {
    if [[ -n "$server_pid" ]]; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -rf "$test_dir"
}
trap cleanup EXIT

assert_contains() {
    if ! grep -Fq "$2" "$1"; then
        echo "Не найдено в $(basename "$1"): $2" >&2
        tail -40 "$1" >&2
        exit 1
    fi
}

assert_count_at_least() {
    local found
    found="$(grep -Fc "$2" "$1" || true)"
    if (( found < $3 )); then
        echo "Ожидалось не менее $3 строк «$2», найдено $found" >&2
        tail -40 "$1" >&2
        exit 1
    fi
}

start_backend() {
    DB_URL="jdbc:h2:file:$test_dir/funeral-test;MODE=PostgreSQL" SERVER_PORT=0 \
        java -jar "$project_dir/backend/target/backend-1.0-SNAPSHOT.jar" \
        > "$test_dir/server.log" 2>&1 &
    server_pid=$!

    local port=""
    for _ in {1..100}; do
        port="$(grep -oE 'Tomcat started on port [0-9]+' "$test_dir/server.log" \
            | tail -1 | awk '{print $5}' || true)"
        if [[ -n "$port" ]] && curl -fsS "http://localhost:$port/api/clients" > /dev/null; then
            api_url="http://localhost:$port"
            return
        fi
        if ! kill -0 "$server_pid" 2>/dev/null; then break; fi
        sleep 0.2
    done
    echo "Backend не запустился:" >&2
    tail -40 "$test_dir/server.log" >&2
    exit 1
}

stop_backend() {
    kill "$server_pid"
    wait "$server_pid" 2>/dev/null || true
    server_pid=""
}

cd "$project_dir"
mvn -q -pl backend,console-client package > "$test_dir/build.log" 2>&1 || {
    tail -40 "$test_dir/build.log" >&2
    exit 1
}

start_backend
ceremony_date="$(date -u -d '+7 days' +%F)"

(
    cd "$test_dir"
    FUNERAL_API_URL="$api_url" java -jar "$project_dir/console-client/target/console-client-1.0-SNAPSHOT.jar" \
        > "$test_dir/cli-first.log" <<INPUT
9
2
2
0
1
2
Иван
123
+79991234567
bad-email
ivan@example.com
1
3
1
Иван Иванов


1
0
2
2
999
1
Пётр Иванов
wrong
$ceremony_date
OTHER
BURIAL
-1
10000
Проверка
1
3
1






CONFIRMED
1
0
3
Пётр
4
CONFIRMED
BURIAL


5
6
7
1
4
1
0
0
INPUT
)

first="$test_dir/cli-first.log"
assert_contains "$first" "Нет такого пункта меню."
assert_contains "$first" "Сначала добавьте клиента"
assert_contains "$first" "Введите телефон с 10–15 цифрами"
assert_contains "$first" "Введите корректный email"
assert_contains "$first" "Клиент добавлен, ID: 1"
assert_contains "$first" "#1 | Иван Иванов"
assert_contains "$first" "Выберите ID клиента из списка выше."
assert_contains "$first" "Введите дату в формате"
assert_contains "$first" "Введите BURIAL или CREMATION."
assert_contains "$first" "Стоимость должна быть неотрицательным числом."
assert_contains "$first" "Заявка создана:"
assert_contains "$first" "#1 | клиент #1 | Пётр Иванов"
assert_count_at_least "$first" "#1 | клиент #1 | Пётр Иванов" 5
assert_contains "$first" "Заявка изменена."
assert_contains "$first" "| CONFIRMED |"
assert_contains "$first" "Клиентов: 1"
assert_contains "$first" "Заявок: 1"
assert_contains "$first" "Заявки сохранены в"
assert_contains "$first" "Нельзя удалить клиента"
jar tf "$test_dir/funeral-requests.xlsx" > "$test_dir/xlsx-files.txt"
assert_contains "$test_dir/xlsx-files.txt" 'xl/workbook.xml'
curl -fsS "$api_url/api/funeral-requests/sort?field=price&ascending=false" \
    > "$test_dir/sorted.json"
assert_contains "$test_dir/sorted.json" 'Пётр Иванов'

stop_backend
start_backend

(
    cd "$test_dir"
    FUNERAL_API_URL="$api_url" java -jar "$project_dir/console-client/target/console-client-1.0-SNAPSHOT.jar" \
        > "$test_dir/cli-after-restart.log" <<'INPUT'
1
1
0
2
1
4
1
1
0
1
4
1
1
0
0
INPUT
)

second="$test_dir/cli-after-restart.log"
assert_contains "$second" "#1 | Иван Иванов"
assert_contains "$second" "#1 | клиент #1 | Пётр Иванов"
assert_contains "$second" "Заявка удалена."
assert_contains "$second" "Заявок пока нет."
assert_contains "$second" "Клиент удалён."
assert_contains "$second" "Клиентов пока нет."

echo "Полный сценарий CLI и backend прошёл успешно."
