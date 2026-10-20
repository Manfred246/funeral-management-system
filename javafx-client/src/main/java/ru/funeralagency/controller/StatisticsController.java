package ru.funeralagency.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import ru.funeralagency.api.ApiClient;
import ru.funeralagency.api.StatisticsApi;
import ru.funeralagency.dto.StatisticsDto;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class StatisticsController {
    @FXML
    private Label totalClientsLabel;

    @FXML
    private Label totalRequestsLabel;

    @FXML
    private Label newRequestsLabel;

    @FXML
    private Label completedRequestsLabel;

    @FXML
    private Label cancelledRequestsLabel;

    @FXML
    private Label averagePriceLabel;

    private final StatisticsApi statisticsApi;

    public StatisticsController() {
        statisticsApi = new StatisticsApi(new ApiClient());
    }

    public void loadStatistics() {
        try {
            StatisticsDto statistics = statisticsApi.getStatistics();
            NumberFormat moneyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU"));

            totalClientsLabel.setText(Long.toString(statistics.getTotalClients()));
            totalRequestsLabel.setText(Long.toString(statistics.getTotalRequests()));
            newRequestsLabel.setText(Long.toString(statistics.getNewRequests()));
            completedRequestsLabel.setText(Long.toString(statistics.getCompletedRequests()));
            cancelledRequestsLabel.setText(Long.toString(statistics.getCancelledRequests()));
            averagePriceLabel.setText(moneyFormat.format(statistics.getAveragePrice()));
        } catch (IOException exception) {
            showError(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Загрузка статистики прервана.");
        }
    }

    @FXML
    private void handleRefresh() {
        loadStatistics();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) totalClientsLabel.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(totalClientsLabel.getScene().getWindow());
        alert.setTitle("Ошибка");
        alert.setHeaderText("Не удалось загрузить статистику");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
