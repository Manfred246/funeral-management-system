package ru.funeralagency.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.funeralagency.api.ApiClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Меню операций с заявками на организацию ритуала. */
class FuneralRequestMenu {

    private static final String CLIENTS = "/api/clients";
    private static final String REQUESTS = "/api/requests";

    private final ApiClient apiClient;
    private final ConsoleInput input;
    private final ObjectMapper objectMapper = new ObjectMapper();

    FuneralRequestMenu(ApiClient apiClient, ConsoleInput input) {
        this.apiClient = apiClient;
        this.input = input;
    }

    void open() {
        boolean opened = true;
        while (opened) {
            printMenu();
            String choice = input.read("Выберите действие: ");
            opened = handleChoice(choice);
        }
    }

    private boolean handleChoice(String choice) {
        try {
            switch (choice) {
                case "1" -> printRequests(apiClient.get(REQUESTS));
                case "2" -> showById();
                case "3" -> create();
                case "4" -> update();
                case "5" -> delete();
                case "6" -> search();
                case "7" -> filter();
                case "8" -> sort();
                case "0" -> {
                    return false;
                }
                default -> System.out.println("Нет такого пункта меню.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("Операция прервана.");
            return false;
        } catch (IOException | IllegalArgumentException exception) {
            System.out.println("Ошибка: " + exception.getMessage());
        }
        return true;
    }

    private void printMenu() {
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
    }

    private void showById() throws IOException, InterruptedException {
        long id = input.readPositiveLong("ID заявки: ");
        printRequest(apiClient.get(REQUESTS + "/" + id));
    }

    private void create() throws IOException, InterruptedException {
        JsonNode created = apiClient.post(REQUESTS, form(null));
        System.out.println("Заявка создана:");
        printRequest(created);
    }

    private void update() throws IOException, InterruptedException {
        long id = input.readPositiveLong("ID заявки: ");
        JsonNode current = apiClient.get(REQUESTS + "/" + id);
        JsonNode updated = apiClient.put(REQUESTS + "/" + id, form(current));
        System.out.println("Заявка изменена:");
        printRequest(updated);
    }

    private void delete() throws IOException, InterruptedException {
        long id = input.readPositiveLong("ID заявки: ");
        apiClient.delete(REQUESTS + "/" + id);
        System.out.println("Заявка удалена.");
    }

    private ObjectNode form(JsonNode current) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("clientId", chooseClientId(current));
        body.put(
                "deceasedFullName",
                input.readRequiredText(
                        "ФИО умершего: ",
                        value(current, "deceasedFullName"),
                        255
                )
        );
        body.put("ceremonyDate", input.readCeremonyDate(value(current, "ceremonyDate")));
        body.put("ceremonyType", input.readCeremonyType(value(current, "ceremonyType")));
        body.put("price", input.readPrice(value(current, "price")));
        body.put(
                "comment",
                input.readOptionalText("Комментарий", value(current, "comment"), current != null)
        );

        if (current != null) {
            body.put("status", input.readStatus(value(current, "status")));
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
            long id = input.readPositiveLong("ID клиента: ", value(current, "clientId"));
            for (JsonNode client : clients) {
                if (client.path("id").asLong() == id) {
                    return id;
                }
            }
            System.out.println("Выберите ID клиента из списка выше.");
        }
    }

    private void search() throws IOException, InterruptedException {
        System.out.println("1. По части ФИО умершего");
        System.out.println("2. По ID клиента");
        String choice = input.read("Выберите вариант поиска: ");

        String path;
        if (choice.equals("1")) {
            String name = input.readRequiredText("Часть ФИО умершего: ", "", 255);
            path = REQUESTS + "/search?deceasedName=" + encode(name);
        } else if (choice.equals("2")) {
            long clientId = input.readPositiveLong("ID клиента: ");
            path = REQUESTS + "/search?clientId=" + clientId;
        } else {
            throw new IllegalArgumentException("Нет такого варианта поиска.");
        }

        printRequests(apiClient.get(path));
    }

    private void filter() throws IOException, InterruptedException {
        String status = input.readOptionalStatus();
        String ceremonyType = input.readOptionalCeremonyType();
        String dateFrom = input.readOptionalDate(
                "Дата от (ГГГГ-ММ-ДД; пусто — без ограничения): "
        );
        String dateTo = input.readOptionalDate(
                "Дата до (ГГГГ-ММ-ДД; пусто — без ограничения): "
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

    private void sort() throws IOException, InterruptedException {
        System.out.println("1. По дате церемонии");
        System.out.println("2. По стоимости");
        String fieldChoice = input.read("Поле сортировки: ");

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
        String directionChoice = input.read("Направление сортировки: ");

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

    private void printClients(JsonNode clients) {
        for (JsonNode client : clients) {
            System.out.printf(
                    "#%s | %s | %s%n",
                    value(client, "id"),
                    value(client, "fullName"),
                    value(client, "phone")
            );
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
}
