package ru.funeralagency.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.funeralagency.api.ApiClient;

import java.io.IOException;

/** Меню операций с клиентами. */
class ClientMenu {

    private static final String CLIENTS = "/api/clients";

    private final ApiClient apiClient;
    private final ConsoleInput input;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ClientMenu(ApiClient apiClient, ConsoleInput input) {
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
                case "1" -> printClients(apiClient.get(CLIENTS));
                case "2" -> showById();
                case "3" -> create();
                case "4" -> update();
                case "5" -> delete();
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
        System.out.println("\n--- Клиенты ---");
        System.out.println("1. Показать всех клиентов");
        System.out.println("2. Показать клиента по ID");
        System.out.println("3. Добавить клиента");
        System.out.println("4. Изменить клиента");
        System.out.println("5. Удалить клиента");
        System.out.println("0. Главное меню");
    }

    private void showById() throws IOException, InterruptedException {
        long id = input.readPositiveLong("ID клиента: ");
        printClient(apiClient.get(CLIENTS + "/" + id));
    }

    private void create() throws IOException, InterruptedException {
        JsonNode created = apiClient.post(CLIENTS, form(null));
        System.out.println("Клиент добавлен:");
        printClient(created);
    }

    private void update() throws IOException, InterruptedException {
        long id = input.readPositiveLong("ID клиента: ");
        JsonNode current = apiClient.get(CLIENTS + "/" + id);
        JsonNode updated = apiClient.put(CLIENTS + "/" + id, form(current));
        System.out.println("Клиент изменён:");
        printClient(updated);
    }

    private void delete() throws IOException, InterruptedException {
        long id = input.readPositiveLong("ID клиента: ");
        apiClient.delete(CLIENTS + "/" + id);
        System.out.println("Клиент удалён.");
    }

    private ObjectNode form(JsonNode current) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put(
                "fullName",
                input.readRequiredText("ФИО клиента: ", value(current, "fullName"), 255)
        );
        body.put("phone", input.readPhone(value(current, "phone")));
        body.put("email", input.readEmail(value(current, "email"), current != null));
        return body;
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

    private String value(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        return node.path(field).asText("");
    }
}
