package ru.funeralagency.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.funeralagency.api.ApiClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ConsoleMenu {

    private static final String CLIENTS = "/api/clients";
    private static final String REQUESTS = "/api/funeral-requests";
    private static final Pattern CLIENT_NAME = Pattern.compile(
            "^[\\p{L}]+(?:['’\\-][\\p{L}]+)*(?:\\s+[\\p{L}]+(?:['’\\-][\\p{L}]+)*){0,2}$");
    private static final Pattern PHONE = Pattern.compile(
            "^(?=(?:\\D*\\d){10,15}\\D*$)\\+?[0-9][0-9 ()-]*$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Scanner input = new Scanner(System.in);
    private final ApiClient api = new ApiClient();
    private final ObjectMapper json = new ObjectMapper();

    public void run() {
        System.out.println("Система управления ритуальными услугами «Боже»");
        try {
            while (true) {
                System.out.println("\n========== ГЛАВНОЕ МЕНЮ ==========");
                System.out.println("1. Клиенты");
                System.out.println("2. Заявки");
                System.out.println("3. Поиск заявок");
                System.out.println("4. Фильтрация заявок");
                System.out.println("5. Статистика");
                System.out.println("6. Экспорт заявок в Excel");
                System.out.println("7. Показать данные клиентов и заявок");
                System.out.println("0. Выход");
                String choice = ask("Выберите действие: ");
                if (choice.equals("0")) {
                    break;
                }
                perform(() -> {
                    switch (choice) {
                        case "1" -> clientsMenu();
                        case "2" -> requestsMenu();
                        case "3" -> search();
                        case "4" -> filter();
                        case "5" -> statistics();
                        case "6" -> export();
                        case "7" -> showAll();
                        default -> System.out.println("Нет такого пункта меню.");
                    }
                });
            }
        } catch (NoSuchElementException e) {
            // Конец ввода, например Ctrl+D.
        }
        System.out.println("До свидания!");
    }

    private void clientsMenu() {
        while (true) {
            System.out.println("\n--- Клиенты ---");
            System.out.println("1. Список клиентов");
            System.out.println("2. Добавить клиента");
            System.out.println("3. Изменить клиента");
            System.out.println("4. Удалить клиента");
            System.out.println("0. Главное меню");
            String choice = ask("Выберите действие: ");
            if (choice.equals("0")) return;
            perform(() -> {
                switch (choice) {
                    case "1" -> printClients(api.get(CLIENTS));
                    case "2" -> {
                        JsonNode created = api.post(CLIENTS, clientForm(null));
                        System.out.println("Клиент добавлен, ID: " + created.path("id").asText());
                    }
                    case "3" -> {
                        long id = askId("ID клиента: ");
                        JsonNode current = api.get(CLIENTS + "/" + id);
                        api.put(CLIENTS + "/" + id, clientForm(current));
                        System.out.println("Клиент изменён.");
                    }
                    case "4" -> {
                        api.delete(CLIENTS + "/" + askId("ID клиента: "));
                        System.out.println("Клиент удалён.");
                    }
                    default -> System.out.println("Нет такого пункта меню.");
                }
            });
        }
    }

    private void requestsMenu() {
        while (true) {
            System.out.println("\n--- Заявки ---");
            System.out.println("1. Список заявок");
            System.out.println("2. Создать заявку");
            System.out.println("3. Изменить заявку");
            System.out.println("4. Удалить заявку");
            System.out.println("0. Главное меню");
            String choice = ask("Выберите действие: ");
            if (choice.equals("0")) return;
            perform(() -> {
                switch (choice) {
                    case "1" -> printRequests(api.get(REQUESTS));
                    case "2" -> {
                        JsonNode created = api.post(REQUESTS, requestForm(null));
                        System.out.println("Заявка создана:");
                        printRequest(created);
                    }
                    case "3" -> {
                        long id = askId("ID заявки: ");
                        JsonNode current = api.get(REQUESTS + "/" + id);
                        api.put(REQUESTS + "/" + id, requestForm(current));
                        System.out.println("Заявка изменена.");
                    }
                    case "4" -> {
                        api.delete(REQUESTS + "/" + askId("ID заявки: "));
                        System.out.println("Заявка удалена.");
                    }
                    default -> System.out.println("Нет такого пункта меню.");
                }
            });
        }
    }

    private ObjectNode clientForm(JsonNode current) {
        ObjectNode body = json.createObjectNode();
        body.put("fullName", askValid("ФИО клиента (1–3 слова): ", value(current, "fullName"),
                text -> text.length() <= 100 && CLIENT_NAME.matcher(text).matches(),
                "Введите имя из 1–3 слов, используя буквы, дефис или апостроф."));
        body.put("phone", askValid("Телефон (10–15 цифр): ", value(current, "phone"),
                this::validPhone,
                "Введите телефон с 10–15 цифрами, например +79991234567."));
        String email;
        do {
            email = askOptional("Email", value(current, "email"), current != null);
            if (email.isEmpty() || email.length() <= 254 && EMAIL.matcher(email).matches()) break;
            System.out.println("Введите корректный email или оставьте поле пустым.");
        } while (true);
        body.put("email", email);
        return body;
    }

    private ObjectNode requestForm(JsonNode current) throws IOException, InterruptedException {
        ObjectNode body = json.createObjectNode();
        body.put("clientId", chooseClientId(current));
        body.put("deceasedFullName", askValid("ФИО умершего: ", value(current, "deceasedFullName"),
                text -> !text.isBlank() && text.length() <= 150,
                "ФИО умершего обязательно (до 150 символов)."));
        String oldDate = value(current, "ceremonyDate");
        String date = askValid("Дата церемонии (ГГГГ-ММ-ДД): ", oldDate,
                text -> validDate(text) && (text.equals(oldDate) || !LocalDate.parse(text).isBefore(LocalDate.now())),
                "Введите дату в формате ГГГГ-ММ-ДД, не раньше сегодняшнего дня.");
        body.put("ceremonyDate", date);
        String type = askValid("Тип (BURIAL — погребение, CREMATION — кремация): ",
                value(current, "ceremonyType"), this::validType,
                "Введите BURIAL или CREMATION.").toUpperCase();
        body.put("ceremonyType", type);
        String price = askValid("Стоимость: ", value(current, "price"), this::validPrice,
                "Стоимость должна быть неотрицательным числом.");
        body.put("price", price);
        body.put("comment", askOptional("Комментарий", value(current, "comment"), current != null));
        if (current != null) {
            String status = askValid(
                    "Статус (NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED): ",
                    value(current, "status"), this::validStatus,
                    "Введите статус из списка.");
            body.put("status", status.toUpperCase());
        }
        return body;
    }

    private long chooseClientId(JsonNode current) throws IOException, InterruptedException {
        JsonNode clients = api.get(CLIENTS);
        if (clients.isEmpty()) {
            throw new IllegalArgumentException("Сначала добавьте клиента в разделе «Клиенты».");
        }
        System.out.println("Доступные клиенты:");
        printClients(clients);
        while (true) {
            String entered = askWithDefault("ID клиента: ", value(current, "clientId"));
            try {
                long id = positiveLong(entered);
                for (JsonNode client : clients) {
                    if (client.path("id").asLong() == id) return id;
                }
            } catch (IllegalArgumentException ignored) {
                // Попросим выбрать существующий ID.
            }
            System.out.println("Выберите ID клиента из списка выше.");
        }
    }

    private void search() throws IOException, InterruptedException {
        String query = ask("Поиск по заявкам: ");
        printRequests(api.get(REQUESTS + "/search?query=" + encode(query)));
    }

    private void filter() throws IOException, InterruptedException {
        String status = askValid(
                "Статус (NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED; пусто — любой): ",
                "", text -> text.isEmpty() || validStatus(text), "Введите статус из списка.")
                .toUpperCase();
        String type = askValid("Тип (BURIAL, CREMATION; пусто — любой): ",
                "", text -> text.isEmpty() || validType(text), "Введите BURIAL или CREMATION.")
                .toUpperCase();
        String from = askValid("Дата от (ГГГГ-ММ-ДД; пусто — без ограничения): ",
                "", text -> text.isEmpty() || validDate(text), "Введите дату в формате ГГГГ-ММ-ДД.");
        String to = askValid("Дата до (ГГГГ-ММ-ДД; пусто — без ограничения): ",
                "", text -> text.isEmpty() || validDate(text), "Введите дату в формате ГГГГ-ММ-ДД.");
        StringBuilder path = new StringBuilder(REQUESTS + "/filter?");
        addParameter(path, "status", status);
        addParameter(path, "ceremonyType", type);
        addParameter(path, "dateFrom", from);
        addParameter(path, "dateTo", to);
        printRequests(api.get(path.toString()));
    }

    private void statistics() throws IOException, InterruptedException {
        JsonNode requests = api.get(REQUESTS);
        BigDecimal total = BigDecimal.ZERO;
        int newCount = 0;
        int completed = 0;
        for (JsonNode request : requests) {
            total = total.add(new BigDecimal(request.path("price").asText("0")));
            if (request.path("status").asText().equals("NEW")) newCount++;
            if (request.path("status").asText().equals("COMPLETED")) completed++;
        }
        System.out.println("Клиентов: " + api.get(CLIENTS).size());
        System.out.println("Заявок: " + requests.size());
        System.out.println("Новых: " + newCount + ", завершённых: " + completed);
        System.out.println("Общая стоимость заявок: " + total);
    }

    private void export() throws IOException, InterruptedException {
        Path file = Path.of("funeral-requests.xlsx");
        Files.write(file, api.download(REQUESTS + "/export"));
        System.out.println("Заявки сохранены в " + file.toAbsolutePath());
    }

    private void showAll() throws IOException, InterruptedException {
        System.out.println("\nКлиенты:");
        printClients(api.get(CLIENTS));
        System.out.println("\nЗаявки:");
        printRequests(api.get(REQUESTS));
    }

    private void printClients(JsonNode clients) {
        if (clients.isEmpty()) {
            System.out.println("Клиентов пока нет.");
            return;
        }
        for (JsonNode client : clients) {
            System.out.printf("#%s | %s | %s | %s%n",
                    value(client, "id"), value(client, "fullName"),
                    value(client, "phone"), value(client, "email"));
        }
    }

    private void printRequests(JsonNode requests) {
        if (requests.isEmpty()) {
            System.out.println("Заявок пока нет.");
            return;
        }
        for (JsonNode request : requests) {
            printRequest(request);
        }
    }

    private void printRequest(JsonNode request) {
        System.out.printf("#%s | клиент #%s | %s | %s | %s | %s | %s%n",
                value(request, "id"), value(request, "clientId"),
                value(request, "deceasedFullName"), value(request, "ceremonyDate"),
                value(request, "ceremonyType"), value(request, "status"), value(request, "price"));
    }

    private void perform(Action action) {
        try {
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Операция прервана.");
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return input.nextLine().trim();
    }

    private String askWithDefault(String prompt, String current) {
        String suffix = current.isEmpty() ? "" : " [" + current + "]";
        String entered = ask(prompt.stripTrailing() + suffix + " ");
        return entered.isEmpty() ? current : entered;
    }

    private String askValid(String prompt, String current, Predicate<String> valid, String error) {
        while (true) {
            String entered = askWithDefault(prompt, current);
            if (valid.test(entered)) return entered;
            System.out.println(error);
        }
    }

    private boolean validDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean validPhone(String text) {
        if (text.length() > 25 || !PHONE.matcher(text).matches() || text.contains("()")) return false;
        int opened = 0;
        for (char character : text.toCharArray()) {
            if (character == '(') opened++;
            if (character == ')' && --opened < 0) return false;
        }
        return opened == 0;
    }

    private boolean validPrice(String text) {
        try {
            return new BigDecimal(text).signum() >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validType(String text) {
        return text.equalsIgnoreCase("BURIAL") || text.equalsIgnoreCase("CREMATION");
    }

    private boolean validStatus(String text) {
        return text.equalsIgnoreCase("NEW") || text.equalsIgnoreCase("CONFIRMED")
                || text.equalsIgnoreCase("IN_PROGRESS") || text.equalsIgnoreCase("COMPLETED")
                || text.equalsIgnoreCase("CANCELLED");
    }

    private String askOptional(String label, String current, boolean updating) {
        if (!updating) return ask(label + " (можно оставить пустым): ");
        String entered = ask(label + " [" + current + "] (Enter — оставить, - — очистить): ");
        return entered.equals("-") ? "" : entered.isEmpty() ? current : entered;
    }

    private long askId(String prompt) {
        return positiveLong(ask(prompt));
    }

    private long positiveLong(String text) {
        long id = Long.parseLong(text);
        if (id <= 0) throw new IllegalArgumentException("ID должен быть положительным числом.");
        return id;
    }

    private String value(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private void addParameter(StringBuilder path, String name, String value) {
        if (!value.isEmpty()) path.append(name).append('=').append(encode(value)).append('&');
    }

    @FunctionalInterface
    private interface Action {
        void run() throws IOException, InterruptedException;
    }
}
