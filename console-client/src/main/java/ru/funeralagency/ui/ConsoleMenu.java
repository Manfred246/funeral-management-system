package ru.funeralagency.ui;

import com.fasterxml.jackson.databind.JsonNode;
import ru.funeralagency.api.ApiClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Главное меню консольного клиента. */
public class ConsoleMenu {

    private static final String STATISTICS = "/api/statistics";
    private static final String REQUEST_EXPORT = "/api/requests/export";

    private final ApiClient apiClient;
    private final ConsoleInput input;
    private final ClientMenu clientMenu;
    private final FuneralRequestMenu requestMenu;

    public ConsoleMenu() {
        apiClient = new ApiClient();
        input = new ConsoleInput(new Scanner(System.in));
        clientMenu = new ClientMenu(apiClient, input);
        requestMenu = new FuneralRequestMenu(apiClient, input);
    }

    public void run() {
        System.out.println("Funeral Management System");
        try {
            boolean running = true;
            while (running) {
                printMainMenu();
                String choice = input.read("Выберите действие: ");
                running = handleChoice(choice);
            }
        } catch (NoSuchElementException exception) {
            // Пользователь закрыл поток консольного ввода.
        }
        System.out.println("До свидания!");
    }

    private boolean handleChoice(String choice) {
        try {
            switch (choice) {
                case "1" -> clientMenu.open();
                case "2" -> requestMenu.open();
                case "3" -> showStatistics();
                case "4" -> exportRequests();
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
        return !Thread.currentThread().isInterrupted();
    }

    private void printMainMenu() {
        System.out.println("\n========== ГЛАВНОЕ МЕНЮ ==========");
        System.out.println("1. Клиенты");
        System.out.println("2. Заявки");
        System.out.println("3. Статистика");
        System.out.println("4. Экспорт заявок в Excel");
        System.out.println("0. Выход");
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
        Files.write(file, apiClient.download(REQUEST_EXPORT));
        System.out.println("Заявки сохранены в " + file.toAbsolutePath());
    }

    private String value(JsonNode node, String field) {
        return node.path(field).asText("");
    }
}
