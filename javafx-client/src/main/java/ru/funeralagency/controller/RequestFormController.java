package ru.funeralagency.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.funeralagency.api.FuneralRequestApi;
import ru.funeralagency.dto.CeremonyType;
import ru.funeralagency.dto.ClientDTO;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestDTO;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.dto.RequestStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

public class RequestFormController {
    @FXML
    private Label formTitle;

    @FXML
    private ComboBox<ClientDTO> clientComboBox;

    @FXML
    private TextField deceasedNameField;

    @FXML
    private DatePicker ceremonyDatePicker;

    @FXML
    private ComboBox<CeremonyType> ceremonyTypeComboBox;

    @FXML
    private Label statusLabel;

    @FXML
    private ComboBox<RequestStatus> statusComboBox;

    @FXML
    private TextField priceField;

    @FXML
    private TextArea commentArea;

    private FuneralRequestApi requestApi;
    private Long requestId;
    private boolean saved;

    public void initializeForm(FuneralRequestApi requestApi, List<ClientDTO> clients) {
        this.requestApi = requestApi;
        clientComboBox.setItems(FXCollections.observableArrayList(clients));
        ceremonyTypeComboBox.setItems(FXCollections.observableArrayList(CeremonyType.values()));
        statusComboBox.setItems(FXCollections.observableArrayList(RequestStatus.values()));
    }

    public void initializeEditForm(
            FuneralRequestApi requestApi, List<ClientDTO> clients, FuneralRequestDTO request
    ) {
        initializeForm(requestApi, clients);
        requestId = request.getId();
        formTitle.setText("Изменение заявки");
        for (ClientDTO client : clients) {
            if (client.getId().equals(request.getClientId())) {
                clientComboBox.setValue(client);
                break;
            }
        }
        deceasedNameField.setText(request.getDeceasedFullName());
        ceremonyDatePicker.setValue(request.getCeremonyDate());
        ceremonyTypeComboBox.setValue(request.getCeremonyType());
        priceField.setText(request.getPrice().toPlainString());
        commentArea.setText(request.getComment() == null ? "" : request.getComment());
        statusComboBox.setValue(request.getStatus());
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        statusComboBox.setVisible(true);
        statusComboBox.setManaged(true);
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleSave() {
        if (clientComboBox.getValue() == null
                || deceasedNameField.getText().isBlank()
                || ceremonyDatePicker.getValue() == null
                || ceremonyTypeComboBox.getValue() == null
                || priceField.getText().isBlank()) {
            showError("Заполните обязательные поля: клиент, ФИО умершего, дата, тип церемонии и стоимость.");
            return;
        }

        if (requestId != null && statusComboBox.getValue() == null) {
            showError("Выберите статус заявки.");
            return;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(priceField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            showError("Введите стоимость числом, например 45000.00.");
            return;
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            showError("Стоимость не может быть отрицательной.");
            return;
        }

        try {
            if (requestId == null) {
                FuneralRequestCreateDto request = new FuneralRequestCreateDto(
                        clientComboBox.getValue().getId(),
                        deceasedNameField.getText().trim(),
                        ceremonyDatePicker.getValue(),
                        ceremonyTypeComboBox.getValue(),
                        price,
                        commentArea.getText().trim()
                );
                requestApi.create(request);
            } else {
                FuneralRequestUpdateDto request = new FuneralRequestUpdateDto(
                        clientComboBox.getValue().getId(),
                        deceasedNameField.getText().trim(),
                        ceremonyDatePicker.getValue(),
                        ceremonyTypeComboBox.getValue(),
                        statusComboBox.getValue(),
                        price,
                        commentArea.getText().trim()
                );
                requestApi.update(requestId, request);
            }
            saved = true;
            closeWindow();
        } catch (IOException exception) {
            showError(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showError("Сохранение заявки прервано.");
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) clientComboBox.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(clientComboBox.getScene().getWindow());
        alert.setTitle("Ошибка");
        alert.setHeaderText("Не удалось сохранить заявку");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
