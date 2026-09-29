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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Консольное меню, которое работает с backend только через HTTP. */
public class ConsoleMenu {

    private static final String CLIENTS = "/api/clients";
    private static final String REQUESTS = "/api/requests";
    private static final String STATISTICS = "/api/statistics";
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private static final Pattern PHONE = Pattern.compile(
            "^\\+?[0-9][0-9 ()-]*$"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final Scanner input = new Scanner(System.in);
    private final ApiClient apiClient = new ApiClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void run() {
        System.out.println("Funeral Management System");
        try {
            boolean running = true;
            while (running) {
                printMainMenu();
                String choice = ask("Выберите действие: ");
                switch (choice) {
                    case "1" -> clientsMenu();
                    case "2" -> requestsMenu();
                    case "3" -> perform(this::showStatistics);
                    case "4" -> perform(this::exportRequests);
                    case "0" -> running = false;
                    default -> System.out.println("Нет такого пункта меню.");
                }
                if (Thread.currentThread().isInterrupted()) {
                    running = false;
                }
            }
        } catch (NoSuchElementException exception) {
            // Пользователь закрыл поток консольного ввода.
        }
        System.out.println("До свидания!");
    }

    private void printMainMenu() {
        System.out.println("\n========== ГЛАВНОЕ МЕНЮ ==========");
        System.out.println("1. Клиенты");
        System.out.println("2. Заявки");
        System.out.println("3. Статистика");
        System.out.println("4. Экспорт заявок в Excel");
        System.out.println("0. Выход");
    }

    private void clientsMenu() {
        boolean opened = true;
        while (opened) {
            System.out.println("\n--- Клиенты ---");
            System.out.println("1. Показать всех клиентов");
            System.out.println("2. Показать клиента по ID");
            System.out.println("3. Добавить клиента");
            System.out.println("4. Изменить клиента");
            System.out.println("5. Удалить клиента");
            System.out.println("0. Главное меню");
            String choice = ask("Выберите действие: ");

            switch (choice) {
                case "1" -> perform(() -> printClients(apiClient.get(CLIENTS)));
                case "2" -> perform(() -> printClient(
                        apiClient.get(CLIENTS + "/" + askId("ID клиента: "))
                ));
                case "3" -> perform(this::createClient);
                case "4" -> perform(this::updateClient);
                case "5" -> perform(this::deleteClient);
                case "0" -> opened = false;
                default -> System.out.println("Нет такого пункта меню.");
            }
            if (Thread.currentThread().isInterrupted()) {
                opened = false;
            }
        }
    }

    private void createClient() throws IOException, InterruptedException {
        JsonNode created = apiClient.post(CLIENTS, clientForm(null));
        System.out.println("Клиент добавлен:");
        printClient(created);
    }

    private void updateClient() throws IOException, InterruptedException {
        long id = askId("ID клиента: ");
        JsonNode current = apiClient.get(CLIENTS + "/" + id);
        JsonNode updated = apiClient.put(CLIENTS + "/" + id, clientForm(current));
        System.out.println("Клиент изменён:");
        printClient(updated);
    }

    private void deleteClient() throws IOException, InterruptedException {
        long id = askId("ID клиента: ");
        apiClient.delete(CLIENTS + "/" + id);
        System.out.println("Клиент удалён.");
    }

    private void requestsMenu() {
        boolean opened = true;
        while (opened) {
            System.out.println("\n--- Заявки ---");
            System.out.println("1. Показать все заявки");
            System.out.println("2. Показать заявку по ID");
            System.out.println("3. Создать заявку");
            System.out.println("4. Изменить заявку");
            System.out.println("5. Удалить заявку");
            System.out.println("6. Поиск заявок");
            System.out.println("7. Фильтрация заявок");
            System.out.println("8. Сортировка заявок");
            System.out.println("0. Главное меню");
            String choice = ask("Выберите действие: ");

            switch (choice) {
                case "1" -> perform(() -> printRequests(apiClient.get(REQUESTS)));
                case "2" -> perform(() -> printRequest(
                        apiClient.get(REQUESTS + "/" + askId("ID заявки: "))
                ));
                case "3" -> perform(this::createRequest);
                case "4" -> perform(this::updateRequest);
                case "5" -> perform(this::deleteRequest);
                case "6" -> perform(this::searchRequests);
                case "7" -> perform(this::filterRequests);
                case "8" -> perform(this::sortRequests);
                case "0" -> opened = false;
                default -> System.out.println("Нет такого пункта меню.");
            }
            if (Thread.currentThread().isInterrupted()) {
                opened = false;
            }
        }
    }

    private void createRequest() throws IOException, InterruptedException {
        JsonNode created = apiClient.post(REQUESTS, requestForm(null));
        System.out.println("Заявка создана:");
        printRequest(created);
    }

    private void updateRequest() throws IOException, InterruptedException {
        long id = askId("ID заявки: ");
        JsonNode current = apiClient.get(REQUESTS + "/" + id);
        JsonNode updated = apiClient.put(REQUESTS + "/" + id, requestForm(current));
        System.out.println("Заявка изменена:");
        printRequest(updated);
    }

    private void deleteRequest() throws IOException, InterruptedException {
        long id = askId("ID заявки: ");
        apiClient.delete(REQUESTS + "/" + id);
        System.out.println("Заявка удалена.");
    }

    private ObjectNode clientForm(JsonNode current) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put(
                "fullName",
                askValid(
                        "ФИО клиента: ",
                        value(current, "fullName"),
                        text -> !text.isBlank() && text.length() <= 255,
                        "ФИО обязательно и не должно превышать 255 символов."
                )
        );
        body.put(
                "phone",
                askValid(
                        "Телефон: ",
                        value(current, "phone"),
                        this::validPhone,
                        "Введите телефон с 10–15 цифрами, например +79991234567."
                )
        );

        String email;
        do {
            email = askOptional("Email", value(current, "email"), current != null);
            if (email.isEmpty() || email.length() <= 255 && EMAIL.matcher(email).matches()) {
                break;
            }
            System.out.println("Введите корректный email или оставьте поле пустым.");
        } while (true);
        body.put("email", email);
        return body;
    }

    private ObjectNode requestForm(JsonNode current) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("clientId", chooseClientId(current));
        body.put(
                "deceasedFullName",
                askValid(
                        "ФИО умершего: ",
                        value(current, "deceasedFullName"),
                        text -> !text.isBlank() && text.length() <= 255,
                        "ФИО обязательно и не должно превышать 255 символов."
                )
        );

        String previousDate = value(current, "ceremonyDate");
        String ceremonyDate = askValid(
                "Дата церемонии (ГГГГ-ММ-ДД): ",
                previousDate,
                text -> validDate(text)
                        && (text.equals(previousDate)
                        || !LocalDate.parse(text).isBefore(LocalDate.now())),
                "Введите дату в формате ГГГГ-ММ-ДД, не раньше сегодняшнего дня."
        );
        body.put("ceremonyDate", ceremonyDate);

        String ceremonyType = askValid(
                "Тип (BURIAL — захоронение, CREMATION — кремация): ",
                value(current, "ceremonyType"),
                this::validCeremonyType,
                "Введите BURIAL или CREMATION."
        );
        body.put("ceremonyType", ceremonyType.toUpperCase());

        String price = askValid(
                "Стоимость: ",
                value(current, "price"),
                this::validPrice,
                "Введите сумму от 0 до 9999999999.99 с точностью до копейки."
        );
        body.put("price", new BigDecimal(price));
        body.put(
                "comment",
                askOptional("Комментарий", value(current, "comment"), current != null)
        );

        if (current != null) {
            String status = askValid(
                    "Статус (NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED): ",
                    value(current, "status"),
                    this::validStatus,
                    "Введите статус из списка."
            );
            body.put("status", status.toUpperCase());
        }
        return body;
    }

    private long chooseClientId(JsonNode current) throws IOException, InterruptedException {
        JsonNode clients = apiClient.get(CLIENTS);
        if (clients.isEmpty()) {
            throw new IllegalArgumentException(
                    "Сначала добавьте клиента в разделе «Клиенты»."
            );
        }

        System.out.println("Доступные клиенты:");
        printClients(clients);
        while (true) {
            String entered = askWithDefault("ID клиента: ", value(current, "clientId"));
            try {
                long id = positiveLong(entered);
                for (JsonNode client : clients) {
                    if (client.path("id").asLong() == id) {
                        return id;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Ниже выводится одно понятное сообщение для любого неверного ID.
            }
            System.out.println("Выберите ID клиента из списка выше.");
        }
    }

    private void searchRequests() throws IOException, InterruptedException {
        System.out.println("1. По части ФИО умершего");
        System.out.println("2. По ID клиента");
        String choice = ask("Выберите вариант поиска: ");

        String path;
        if (choice.equals("1")) {
            String name = askValid(
                    "Часть ФИО умершего: ",
                    "",
                    text -> !text.isBlank(),
                    "Строка поиска не может быть пустой."
            );
            path = REQUESTS + "/search?deceasedName=" + encode(name);
        } else if (choice.equals("2")) {
            long clientId = askId("ID клиента: ");
            path = REQUESTS + "/search?clientId=" + clientId;
        } else {
            throw new IllegalArgumentException("Нет такого варианта поиска.");
        }

        printRequests(apiClient.get(path));
    }

    private void filterRequests() throws IOException, InterruptedException {
        String status = askValid(
                "Статус (NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED; пусто — любой): ",
                "",
                text -> text.isEmpty() || validStatus(text),
                "Введите статус из списка."
        ).toUpperCase();
        String ceremonyType = askValid(
                "Тип (BURIAL, CREMATION; пусто — любой): ",
                "",
                text -> text.isEmpty() || validCeremonyType(text),
                "Введите BURIAL или CREMATION."
        ).toUpperCase();
        String dateFrom = askValid(
                "Дата от (ГГГГ-ММ-ДД; пусто — без ограничения): ",
                "",
                text -> text.isEmpty() || validDate(text),
                "Введите дату в формате ГГГГ-ММ-ДД."
        );
        String dateTo = askValid(
                "Дата до (ГГГГ-ММ-ДД; пусто — без ограничения): ",
                "",
                text -> text.isEmpty() || validDate(text),
                "Введите дату в формате ГГГГ-ММ-ДД."
        );

        if (!dateFrom.isEmpty() && !dateTo.isEmpty()
                && LocalDate.parse(dateFrom).isAfter(LocalDate.parse(dateTo))) {
            throw new IllegalArgumentException(
                    "Начальная дата не может быть позже конечной."
            );
        }

        List<String> parameters = new ArrayList<>();
        addParameter(parameters, "status", status);
        addParameter(parameters, "ceremonyType", ceremonyType);
        addParameter(parameters, "dateFrom", dateFrom);
        addParameter(parameters, "dateTo", dateTo);

        String path = REQUESTS + "/filter";
        if (!parameters.isEmpty()) {
            path += "?" + String.join("&", parameters);
        }
        printRequests(apiClient.get(path));
    }

    private void sortRequests() throws IOException, InterruptedException {
        System.out.println("1. По дате церемонии");
        System.out.println("2. По стоимости");
        String fieldChoice = ask("Поле сортировки: ");

        String field;
        if (fieldChoice.equals("1")) {
            field = "ceremonyDate";
        } else if (fieldChoice.equals("2")) {
            field = "price";
        } else {
            throw new IllegalArgumentException("Нет такого поля сортировки.");
        }

        System.out.println("1. По возрастанию");
        System.out.println("2. По убыванию");
        String directionChoice = ask("Направление сортировки: ");

        boolean ascending;
        if (directionChoice.equals("1")) {
            ascending = true;
        } else if (directionChoice.equals("2")) {
            ascending = false;
        } else {
            throw new IllegalArgumentException("Нет такого направления сортировки.");
        }

        String path = REQUESTS + "/sort?field=" + field + "&ascending=" + ascending;
        printRequests(apiClient.get(path));
    }

    private void showStatistics() throws IOException, InterruptedException {
        JsonNode statistics = apiClient.get(STATISTICS);
        System.out.println("\n--- Статистика ---");
        System.out.println("Всего клиентов: " + value(statistics, "totalClients"));
        System.out.println("Всего заявок: " + value(statistics, "totalRequests"));
        System.out.println("Новых заявок: " + value(statistics, "newRequests"));
        System.out.println("Завершённых заявок: " + value(statistics, "completedRequests"));
        System.out.println("Отменённых заявок: " + value(statistics, "cancelledRequests"));
        System.out.println("Средняя стоимость: " + value(statistics, "averagePrice"));
    }

    private void exportRequests() throws IOException, InterruptedException {
        Path exportDirectory = Path.of("exports");
        Files.createDirectories(exportDirectory);
        Path file = exportDirectory.resolve("funeral-requests.xlsx");
        Files.write(file, apiClient.download(REQUESTS + "/export"));
        System.out.println("Заявки сохранены в " + file.toAbsolutePath());
    }

    private void printClients(JsonNode clients) {
        if (clients.isEmpty()) {
            System.out.println("Клиентов пока нет.");
            return;
        }
        for (JsonNode client : clients) {
            printClient(client);
        }
    }

    private void printClient(JsonNode client) {
        String email = value(client, "email");
        if (email.isEmpty()) {
            email = "email не указан";
        }
        System.out.printf(
                "#%s | %s | %s | %s%n",
                value(client, "id"),
                value(client, "fullName"),
                value(client, "phone"),
                email
        );
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
        System.out.printf(
                "#%s | клиент #%s | %s | %s | %s | %s | %s%n",
                value(request, "id"),
                value(request, "clientId"),
                value(request, "deceasedFullName"),
                value(request, "ceremonyDate"),
                value(request, "ceremonyType"),
                value(request, "status"),
                value(request, "price")
        );
        String comment = value(request, "comment");
        if (!comment.isEmpty()) {
            System.out.println("Комментарий: " + comment);
        }
    }

    private void perform(Action action) {
        try {
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("Операция прервана.");
        } catch (IOException | IllegalArgumentException exception) {
            System.out.println("Ошибка: " + exception.getMessage());
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return input.nextLine().trim();
    }

    private String askWithDefault(String prompt, String current) {
        String suffix = current.isEmpty() ? "" : " [" + current + "]";
        String entered = ask(prompt.stripTrailing() + suffix + " ");
        if (entered.isEmpty()) {
            return current;
        }
        return entered;
    }

    private String askValid(
            String prompt,
            String current,
            Predicate<String> validation,
            String errorMessage
    ) {
        while (true) {
            String entered = askWithDefault(prompt, current);
            if (validation.test(entered)) {
                return entered;
            }
            System.out.println(errorMessage);
        }
    }

    private boolean validDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private boolean validPhone(String text) {
        if (text.length() > 25 || !PHONE.matcher(text).matches() || text.contains("()")) {
            return false;
        }

        int balance = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '(') {
                balance++;
            } else if (character == ')') {
                balance--;
                if (balance < 0) {
                    return false;
                }
            }
        }

        String normalized = text.replaceAll("[ ()-]", "");
        return balance == 0 && normalized.matches("\\+?[0-9]{10,15}");
    }

    private boolean validPrice(String text) {
        try {
            BigDecimal price = new BigDecimal(text);
            return price.compareTo(BigDecimal.ZERO) >= 0
                    && price.compareTo(MAX_PRICE) <= 0
                    && price.stripTrailingZeros().scale() <= 2;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean validCeremonyType(String text) {
        return text.equalsIgnoreCase("BURIAL") || text.equalsIgnoreCase("CREMATION");
    }

    private boolean validStatus(String text) {
        return text.equalsIgnoreCase("NEW")
                || text.equalsIgnoreCase("CONFIRMED")
                || text.equalsIgnoreCase("IN_PROGRESS")
                || text.equalsIgnoreCase("COMPLETED")
                || text.equalsIgnoreCase("CANCELLED");
    }

    private String askOptional(String label, String current, boolean updating) {
        if (!updating) {
            return ask(label + " (можно оставить пустым): ");
        }
        String entered = ask(
                label + " [" + current + "] (Enter — оставить, - — очистить): "
        );
        if (entered.equals("-")) {
            return "";
        }
        if (entered.isEmpty()) {
            return current;
        }
        return entered;
    }

    private long askId(String prompt) {
        return positiveLong(ask(prompt));
    }

    private long positiveLong(String text) {
        try {
            long id = Long.parseLong(text);
            if (id <= 0) {
                throw new IllegalArgumentException("ID должен быть положительным числом.");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Введите целое положительное число.");
        }
    }

    private String value(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        return node.path(field).asText("");
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private void addParameter(List<String> parameters, String name, String value) {
        if (!value.isEmpty()) {
            parameters.add(name + "=" + encode(value));
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws IOException, InterruptedException;
    }
}
